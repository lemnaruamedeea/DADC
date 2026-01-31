package dad.c01;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;

import javax.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class Main {

  private static final String JMS_URL = System.getenv("JMS_URL") != null
      ? System.getenv("JMS_URL") : System.getProperty("jms.url",
      "failover:(tcp://c02:61616)?initialReconnectDelay=1000&maxReconnectDelay=30000&useExponentialBackOff=true");
  private static final String TOPIC_NAME = "bmp.topic";
  private static final int PORT = Integer.parseInt(
      System.getenv("PORT") != null ? System.getenv("PORT") : System.getProperty("port", "7000"));

  private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();
  private Connection jmsConn;
  private Session jmsSession;
  private MessageProducer producer;
  private Topic topic;

  public static void main(String[] args) throws Exception {
    Main app = new Main();
    app.startJms();
    app.startHttp();
  }

  private void startJms() throws Exception {
    ActiveMQConnectionFactory f = new ActiveMQConnectionFactory(JMS_URL);
    for (int i = 0; i < 60; i++) {
      try {
        jmsConn = f.createConnection();
        jmsConn.start();
        jmsSession = jmsConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = jmsSession.createTopic(TOPIC_NAME);
        producer = jmsSession.createProducer(topic);
        System.out.println("[C01] JMS connected to " + JMS_URL + ", topic " + TOPIC_NAME);
        return;
      } catch (Exception e) {
        if (i == 59) throw e;
        System.err.println("JMS connect retry " + (i + 1) + ": " + e.getMessage());
        Thread.sleep(2000);
      }
    }
  }

  private void startHttp() {
    Javalin app = Javalin.create().start(PORT);

    app.before(ctx -> {
      String origin = ctx.header("Origin");
      if ("http://localhost:5173".equals(origin) || "http://127.0.0.1:5173".equals(origin))
        ctx.header("Access-Control-Allow-Origin", origin);
      else
        ctx.header("Access-Control-Allow-Origin", "http://localhost:5173");
      ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      ctx.header("Access-Control-Allow-Headers", "Content-Type");
    });
    app.options("/api/upload", ctx -> ctx.status(204));
    app.options("/api/job-status/{requestId}", ctx -> ctx.status(204));

    app.post("/api/upload", ctx -> {
      try {
        UploadedFile file = ctx.uploadedFile("file");
        String zoomStr = ctx.formParam("zoomPercent");
        System.out.println("[C01] POST /api/upload received, file=" + (file != null ? file.filename() : "null") + " zoomPercent=" + (zoomStr != null ? zoomStr : "null"));
        if (file == null || zoomStr == null) {
          System.err.println("[C01] UPLOAD reject: missing file or zoomPercent");
          ctx.status(400).json(Map.of("error", "Missing file or zoomPercent"));
          return;
        }
        int zoomPercent = Integer.parseInt(zoomStr);
        String requestId = UUID.randomUUID().toString();
        String pictureId = UUID.randomUUID().toString();
        jobs.put(requestId, new JobStatus("pending", null));

        byte[] data = file.content().readAllBytes();
        System.out.println("[C01] UPLOAD image loaded: " + data.length + " bytes, requestId=" + requestId + " pictureId=" + pictureId + " zoom%=" + zoomPercent);

        BytesMessage msg = jmsSession.createBytesMessage();
        msg.setStringProperty("requestId", requestId);
        msg.setIntProperty("zoomPercent", zoomPercent);
        msg.setStringProperty("pictureId", pictureId);
        msg.writeBytes(data);
        producer.send(msg);

        System.out.println("[C01] UPLOAD published to JMS topic " + TOPIC_NAME + " ok");
        ctx.json(Map.of("requestId", requestId, "pictureId", pictureId));
      } catch (NumberFormatException e) {
        System.err.println("[C01] UPLOAD invalid zoomPercent: " + e.getMessage());
        ctx.status(400).json(Map.of("error", "Invalid zoomPercent"));
      } catch (Exception e) {
        System.err.println("[C01] UPLOAD error: " + e.getMessage());
        e.printStackTrace();
        ctx.status(500).json(Map.of("error", e.getMessage() != null ? e.getMessage() : "Upload failed"));
      }
    });

    app.get("/api/job-status/{requestId}", ctx -> {
      String id = ctx.pathParam("requestId");
      JobStatus s = jobs.get(id);
      System.out.println("[C01] GET /api/job-status/" + id + " -> " + (s != null ? s.status : "404"));
      if (s == null) {
        ctx.status(404).json(Map.of("error", "Unknown requestId"));
        return;
      }
      ctx.json(Map.of("status", s.status, "downloadUrl", s.downloadUrl != null ? s.downloadUrl : ""));
    });

    app.get("/job-complete", ctx -> {
      String requestId = ctx.queryParam("requestId");
      String downloadUrl = ctx.queryParam("downloadUrl");
      String error = ctx.queryParam("error");
      System.out.println("[C01] GET /job-complete requestId=" + requestId + " downloadUrl=" + downloadUrl + " error=" + error);
      if (requestId == null) {
        ctx.status(400).result("Missing requestId");
        return;
      }
      JobStatus s = jobs.get(requestId);
      if (s != null) {
        s.status = "ready";
        s.downloadUrl = downloadUrl;
        if (error != null) s.downloadUrl = "error: " + error;
        System.out.println("[C01] JOB-COMPLETE updated job " + requestId + " -> ready, downloadUrl=" + s.downloadUrl);
      }
      ctx.result("ok");
    });

    app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

    app.get("/metrics", ctx -> {
      OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
      Runtime rt = Runtime.getRuntime();
      long total = rt.totalMemory();
      long free = rt.freeMemory();
      double ramUsage = total > 0 ? ((total - free) * 100.0 / total) : 0;
      double load = os.getSystemLoadAverage() >= 0 ? os.getSystemLoadAverage() : 0;
      int processors = os.getAvailableProcessors();
      double cpuUsage = processors > 0 ? Math.min(100, load * 100.0 / processors) : 0;
      ctx.json(Map.of(
          "node", "c01",
          "osName", os.getName() + " " + os.getArch(),
          "cpuUsage", Math.round(cpuUsage * 100) / 100.0,
          "ramUsage", Math.round(ramUsage * 100) / 100.0
      ));
    });

    System.out.println("[C01] listening on " + PORT);
  }

  private static class JobStatus {
    String status;
    String downloadUrl;

    JobStatus(String status, String downloadUrl) {
      this.status = status;
      this.downloadUrl = downloadUrl;
    }
  }
}
