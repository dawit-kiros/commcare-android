package org.commcare.dalvik.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.ResourceInstallUtils;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerRegistrationException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.utils.ConnectivityStatus;
import org.javarosa.core.services.locale.Localization;

/**
 * Allow user to manage app updating:
 * - Check and download the latest update
 * - Stop a downloading update
 * - Apply a downloaded update
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateActivity extends CommCareActivity<UpdateActivity>
        implements TaskListener<Integer, AppInstallStatus> {

    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "update_task_cancelling";
    private static final int DIALOG_UPGRADE_INSTALL = 6;

    private boolean taskIsCancelling;
    private UpdateTask updateTask;
    private UpdateUIState uiState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiState = new UpdateUIState(this);

        loadSaveInstanceState(savedInstanceState);

        setupUpdateTask();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                    savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
        }
    }

    private void setupUpdateTask() {
        updateTask = UpdateTask.getRunningInstance();

        try {
            if (updateTask != null) {
                updateTask.registerTaskListener(this);
            }
        } catch (TaskListenerRegistrationException e) {
            Log.e(TAG, "Attempting to register a TaskListener to an already " +
                    "registered task.");
            uiState.errorUiState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!ConnectivityStatus.isNetworkAvailable(this) &&
                ConnectivityStatus.isAirplaneModeOn(this)) {
            uiState.noConnectivityUiState();
            return;
        }

        int currentProgress = 0;
        int maxProgress = 0;
        if (updateTask != null) {
            currentProgress = updateTask.getProgress();
            maxProgress = updateTask.getMaxProgress();
            if (taskIsCancelling) {
                uiState.cancellingUiState();
            } else {
                setUiStateFromRunningTask(updateTask.getStatus());
            }
        } else {
            pendingUpdateOrIdle();
        }
        uiState.updateProgressBar(currentProgress, maxProgress);
        uiState.refreshStatusText();
    }

    private void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                uiState.downloadingUiState();
                break;
            case PENDING:
                pendingUpdateOrIdle();
                break;
            case FINISHED:
                uiState.errorUiState();
                break;
            default:
                uiState.errorUiState();
        }
    }

    private void pendingUpdateOrIdle() {
        if (ResourceInstallUtils.isUpdateReadyToInstall()) {
            uiState.unappliedUpdateAvailableUiState();
        } else {
            uiState.idleUiState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    private void unregisterTask() {
        if (updateTask != null) {
            try {
                updateTask.unregisterTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to unregister a not previously " +
                        "registered TaskListener.");
            }
            updateTask = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TASK_CANCELLING_KEY, taskIsCancelling);
    }

    @Override
    public void handleTaskUpdate(Integer... vals) {
        int progress = vals[0];
        int max = vals[1];
        uiState.updateProgressBar(progress, max);
        String msg = Localization.get("updates.found",
                new String[]{"" + progress, "" + max});
        uiState.updateProgressText(msg);
    }

    @Override
    public void handleTaskCompletion(AppInstallStatus result) {
        if (result == AppInstallStatus.UpdateStaged) {
            uiState.unappliedUpdateAvailableUiState();
        } else {
            uiState.upToDateUiState();
        }

        unregisterTask();

        uiState.refreshStatusText();
    }

    @Override
    public void handleTaskCancellation(AppInstallStatus result) {
        unregisterTask();

        uiState.idleUiState();
    }

    protected void startUpdateCheck() {
        try {
            updateTask = UpdateTask.getNewInstance();
            updateTask.startPinnedNotification(this);
            updateTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            enterErrorState("There is already an existing update task instance.");
            return;
        } catch (TaskListenerRegistrationException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        String ref = ResourceInstallUtils.getDefaultProfileRef();
        updateTask.execute(ref);
        uiState.downloadingUiState();
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiState.errorUiState();
    }

    public void stopUpdateCheck() {
        if (updateTask != null) {
            updateTask.cancel(true);
            taskIsCancelling = true;
            uiState.cancellingUiState();
        } else {
            uiState.idleUiState();
        }
    }

    /**
     * Block the user with a dialog while the update is finalized.
     */
    protected void lauchUpdateInstallTask() {
        InstallStagedUpdateTask<UpdateActivity> task =
                new InstallStagedUpdateTask<UpdateActivity>(DIALOG_UPGRADE_INSTALL) {

                    @Override
                    protected void deliverResult(UpdateActivity receiver,
                                                 AppInstallStatus result) {
                        if (result == AppInstallStatus.Installed) {
                            uiState.updateInstalledUiState();
                        } else {
                            uiState.errorUiState();
                        }
                    }

                    @Override
                    protected void deliverUpdate(UpdateActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(UpdateActivity receiver,
                                                Exception e) {
                        uiState.errorUiState();
                    }
                };
        task.connect(this);
        task.execute();
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_UPGRADE_INSTALL) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        String title = Localization.get("updates.installing.title");
        String message = Localization.get("updates.installing.message");
        CustomProgressDialog dialog =
                CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }
}
