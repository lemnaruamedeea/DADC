package dad.mdb;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class C03StartupListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    System.out.println("[C03] BMP app started; MDB -> bmp.topic @ c02:61616 (TomEE Plume, remote broker)");
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {}
}
