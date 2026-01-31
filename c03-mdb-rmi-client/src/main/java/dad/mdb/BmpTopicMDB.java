package dad.mdb;

import dad.zoom.ZoomService;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.jms.*;

import javax.naming.InitialContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "bmp.topic"),
    @ActivationConfigProperty(propertyName = "connectionFactoryLookup", propertyValue = "jms/InboundConnectionFactory"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "NonDurable")
})
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class BmpTopicMDB implements MessageListener {

  private static final String C04_HOST = System.getProperty("c04.host", "c04");
  private static final String C05_HOST = System.getProperty("c05.host", "c05");
  private static final String C06_URL = System.getProperty("c06.url", "http://c06:3000");
  private static final String C01_URL = System.getProperty("c01.url", "http://c01:7000");
  private static final int RMI_PORT = Integer.parseInt(System.getProperty("rmi.port", "1099"));

  @Override
  public void onMessage(Message msg) {
    System.out.println("[C03] onMessage HIT, class=" + (msg != null ? msg.getClass().getName() : "null"));
    if (msg == null) return;
    try {
      String requestId = getStringProp(msg, "requestId", "unknown");
      int zoomPercent = getIntProp(msg, "zoomPercent", 100);
      String pictureId = getStringProp(msg, "pictureId", null);
      System.out.println("[C03] MDB received message: requestId=" + requestId + " pictureId=" + pictureId + " zoom%=" + zoomPercent);

      byte[] bmpBytes = msg.getBody(byte[].class);
      if (bmpBytes == null) bmpBytes = new byte[0];
      System.out.println("[C03] MDB image size " + bmpBytes.length + " bytes");
      if (bmpBytes.length == 0) {
        System.err.println("[C03] MDB reject: empty image");
        notifyJobDone(requestId, null, "empty image");
        publishJobDone(requestId, null, "empty image");
        return;
      }

      System.out.println("[C03] RMI call C04 zoom " + zoomPercent + "%");
      ZoomService zoom04 = lookupRmi(C04_HOST);
      byte[] zoomed04 = zoom04.zoom(bmpBytes, zoomPercent);
      System.out.println("[C03] RMI C04 zoom done, result " + zoomed04.length + " bytes");

      System.out.println("[C03] RMI call C05 zoom " + zoomPercent + "%");
      ZoomService zoom05 = lookupRmi(C05_HOST);
      byte[] zoomed05 = zoom05.zoom(bmpBytes, zoomPercent);
      System.out.println("[C03] RMI C05 zoom done, result " + zoomed05.length + " bytes");

      byte[] zoomed = zoomed04;
      System.out.println("[C03] zoom pics united (using C04 result), size " + zoomed.length + " bytes");

      System.out.println("[C03] store in C06 requestId=" + requestId + " pictureId=" + pictureId);
      String downloadUrl = storeInC06(zoomed, requestId, zoomPercent, pictureId);
      System.out.println("[C03] C06 stored, downloadUrl=" + downloadUrl);

      notifyJobDone(requestId, downloadUrl, null);
      System.out.println("[C03] notified C01 job-complete for " + requestId);

      publishJobDone(requestId, downloadUrl, null);
      System.out.println("[C03] published job done to JMS topic for " + requestId);
    } catch (Exception e) {
      System.err.println("[C03] MDB error: " + e.getMessage());
      e.printStackTrace();
      try {
        String requestId = getStringProp(msg, "requestId", "unknown");
        System.err.println("[C03] notifying C01 job-complete (error) for " + requestId);
        notifyJobDone(requestId, null, e.getMessage());
        publishJobDone(requestId, null, e.getMessage());
      } catch (Exception ex) {
        System.err.println("[C03] notifyJobDone failed: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
  }

  private static String getStringProp(Message m, String name, String def) {
    try {
      String v = m.getStringProperty(name);
      return v != null ? v : def;
    } catch (Exception e) { return def; }
  }

  private static int getIntProp(Message m, String name, int def) {
    try { return m.getIntProperty(name); } catch (Exception e) { return def; }
  }

  private ZoomService lookupRmi(String host) throws Exception {
    Registry reg = LocateRegistry.getRegistry(host, RMI_PORT);
    return (ZoomService) reg.lookup("ZoomService");
  }

  private String storeInC06(byte[] bmp, String requestId, int zoomPercent, String pictureId) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(C06_URL + "/api/bmp"))
        .header("Content-Type", "application/octet-stream")
        .header("X-Request-Id", requestId)
        .header("X-Zoom-Percent", String.valueOf(zoomPercent))
        .header("X-Picture-Id", pictureId != null && !pictureId.isEmpty() ? pictureId : "")
        .POST(HttpRequest.BodyPublishers.ofByteArray(bmp))
        .build();
    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (res.statusCode() != 200) throw new RuntimeException("C06 store failed: " + res.body());
    var json = new org.json.JSONObject(res.body());
    return json.optString("downloadUrl", "");
  }

  private void notifyJobDone(String requestId, String downloadUrl, String error) {
    try {
      String qs = "requestId=" + java.net.URLEncoder.encode(requestId, StandardCharsets.UTF_8);
      if (downloadUrl != null && !downloadUrl.isEmpty())
        qs += "&downloadUrl=" + java.net.URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8);
      if (error != null && !error.isEmpty())
        qs += "&error=" + java.net.URLEncoder.encode(error, StandardCharsets.UTF_8);
      String url = C01_URL + "/job-complete?" + qs;
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() >= 200 && res.statusCode() < 300)
        System.out.println("[C03] job-complete callback OK " + res.statusCode() + " -> C01");
      else
        System.err.println("[C03] job-complete callback unexpected " + res.statusCode() + " " + url);
    } catch (Exception e) {
      System.err.println("[C03] job-complete callback failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void publishJobDone(String requestId, String downloadUrl, String error) {
    Connection conn = null;
    try {
      InitialContext ic = new InitialContext();
      ConnectionFactory cf = (ConnectionFactory) ic.lookup("jms/InboundConnectionFactory");
      Topic jobDoneTopic = (Topic) ic.lookup("jms/jobDoneTopic");
      conn = cf.createConnection();
      conn.start();
      try (Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
           MessageProducer producer = session.createProducer(jobDoneTopic)) {
        MapMessage mapMsg = session.createMapMessage();
        mapMsg.setString("requestId", requestId);
        if (downloadUrl != null) mapMsg.setString("downloadUrl", downloadUrl);
        if (error != null) mapMsg.setString("error", error);
        producer.send(mapMsg);
        System.out.println("[C03] published job done to JMS topic job.done.topic requestId=" + requestId);
      }
    } catch (Exception e) {
      System.err.println("[C03] JMS publish job done failed: " + e.getMessage());
    } finally {
      if (conn != null) {
        try { conn.close(); } catch (JMSException ignored) {}
      }
    }
  }

}
