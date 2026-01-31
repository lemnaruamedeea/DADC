package dad.rmi;

import dad.zoom.ZoomService;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;


@WebListener
public class RmiStartupListener implements ServletContextListener {

  private static final int RMI_PORT = Integer.parseInt(System.getProperty("rmi.port", "1099"));
  private static final String BIND_NAME = "ZoomService";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    try {
      Registry registry = LocateRegistry.createRegistry(RMI_PORT);
      ZoomService impl = new ZoomServiceImpl();
      registry.rebind(BIND_NAME, impl);
      System.out.println("C04 RMI ZoomService bound on port " + RMI_PORT);
    } catch (Exception e) {
      throw new RuntimeException("RMI startup failed", e);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    try {
      Registry r = LocateRegistry.getRegistry("localhost", RMI_PORT);
      r.unbind(BIND_NAME);
    } catch (Exception ignored) {
    }
  }
}
