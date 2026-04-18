package android.app;

import android.annotation.TargetApi;

public class ActivityTaskManager {
    @TargetApi(29)
    public static class RootTaskInfo extends TaskInfo {
        public int[] childTaskIds;
    }
}
