package dad.mdb;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

@WebServlet(urlPatterns = "/metrics")
public class MetricsServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    Runtime rt = Runtime.getRuntime();
    long total = rt.totalMemory();
    long free = rt.freeMemory();
    double ramUsage = total > 0 ? ((total - free) * 100.0 / total) : 0;
    double load = os.getSystemLoadAverage() >= 0 ? os.getSystemLoadAverage() : 0;
    int processors = os.getAvailableProcessors();
    double cpuUsage = processors > 0 ? Math.min(100, load * 100.0 / processors) : 0;
    resp.setContentType("application/json");
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().print(String.format(
        "{\"node\":\"c03\",\"osName\":\"%s %s\",\"cpuUsage\":%.2f,\"ramUsage\":%.2f}",
        os.getName().replace("\"", ""), os.getArch(),
        cpuUsage, ramUsage
    ));
  }
}
