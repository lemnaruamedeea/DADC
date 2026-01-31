package dad.zoom;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ZoomService extends Remote {
  byte[] zoom(byte[] bmpBytes, int zoomPercent) throws RemoteException;
}
