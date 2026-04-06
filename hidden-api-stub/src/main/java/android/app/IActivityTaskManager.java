package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.app.ActivityTaskManager;

import java.util.List;

public interface IActivityTaskManager extends IInterface {

    abstract class Stub extends Binder implements IServiceConnection {
        public static IActivityTaskManager asInterface(IBinder obj)
        {
            throw new RuntimeException("Stub!");
        }
    }

    void moveRootTaskToDisplay(int taskId, int displayId);

    void moveStackToDisplay(int stackId, int displayId);

    void registerTaskStackListener(ITaskStackListener iTaskStackListener);

    void unregisterTaskStackListener(ITaskStackListener iTaskStackListener);
    
    void focusTopTask(int displayId);

    List<ActivityTaskManager.RootTaskInfo> getAllRootTaskInfosOnDisplay(int displayId);

    List<Object> getAllStackInfosOnDisplay(int displayId);

    void setFocusedRootTask(int taskId);

    void setFocusedStack(int stackId);
}
