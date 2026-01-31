package dad.rmi;

import dad.zoom.ZoomService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


public class ZoomServiceImpl extends UnicastRemoteObject implements ZoomService {

  public ZoomServiceImpl() throws RemoteException {
    super();
  }

  @Override
  public byte[] zoom(byte[] bmpBytes, int zoomPercent) throws RemoteException {
    System.out.println("[C05] RMI zoom called: input " + (bmpBytes == null ? 0 : bmpBytes.length) + " bytes, zoom%=" + zoomPercent);
    if (bmpBytes == null || bmpBytes.length == 0) return bmpBytes;
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(bmpBytes));
      if (src == null) throw new RemoteException("Cannot decode BMP");
      int srcW = src.getWidth();
      int srcH = src.getHeight();
      double scale = zoomPercent / 100.0;
      int w = Math.max(1, (int) Math.round(srcW * scale));
      int h = Math.max(1, (int) Math.round(srcH * scale));
      System.out.println("[C05] RMI zoom " + srcW + "x" + srcH + " -> " + w + "x" + h);
      BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = dest.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, w, h, null);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(dest, "bmp", out);
      byte[] result = out.toByteArray();
      System.out.println("[C05] RMI zoom done: output " + result.length + " bytes");
      return result;
    } catch (Exception e) {
      System.err.println("[C05] RMI zoom error: " + e.getMessage());
      throw new RemoteException("Zoom failed", e);
    }
  }
}
