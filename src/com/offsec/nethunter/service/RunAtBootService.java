package com.offsec.nethunter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.offsec.nethunter.ChrootManagerFragment;
import com.offsec.nethunter.KaliServicesFragment;
import com.offsec.nethunter.R;
import com.offsec.nethunter.utils.NhPaths;
import com.offsec.nethunter.utils.ShellExecuter;


public class RunAtBootService extends Service {
    private static final String CHROOT_INSTALLED_TAG = "CHROOT_INSTALLED_TAG";
    private static final String TAG = "Nethunter: Startup";
    private final ShellExecuter x = new ShellExecuter();
    private NhPaths nh;
    private Boolean runBootServices = true;
    private String doing_action = "";
    private NotificationCompat.Builder n = null;
    private NotificationManager notificationManager;
    public RunAtBootService() {
    }

    private void doNotification(String contents) {
        //Simon Edit Start
        final int NOTIFY_ID = 1002;
        // There are hardcoding only for show it's just strings
        String name = "my_package_channel";
        String id = "my_package_channel_1"; // The user-visible name of the channel.
        String description = "my_package_first_channel"; // The user-visible description of the channel.

        //Intent intent;
        //PendingIntent pendingIntent;
        if (notificationManager == null) {
            notificationManager =
                    (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        }

        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = notificationManager.getNotificationChannel(id);
        if (mChannel == null) {
            mChannel = new NotificationChannel(id, name, importance);
            mChannel.setDescription(description);
            mChannel.enableVibration(false);
            //mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            notificationManager.createNotificationChannel(mChannel);
        }

        if (n == null) {
            n = new NotificationCompat.Builder(this, id);
        }

        //intent = new Intent(this, MainActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        //.setStyle(new Notification.BigTextStyle().bigText(contents))
        n.setContentTitle(RunAtBootService.TAG)
                //.setContentText(contents)
                .setSmallIcon(R.drawable.ic_stat_ic_nh_notificaiton)
                .setContentText(this.getString(R.string.app_name))
                .setDefaults(Notification.DEFAULT_ALL)
                //.setContentIntent(pendingIntent)
                // .setContentIntent(pIntent)
                .setAutoCancel(true);

                //Simon Edit End
        //NotificationManager notificationManager =
                //(NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        //Simon Edit Start

        // The id of the channel.

        notificationManager.notify(NOTIFY_ID, n.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        doNotification("Doing boot checks");
        nh = new NhPaths(getFilesDir().toString());
        SharedPreferences sharedpreferences = getSharedPreferences("com.offsec.nethunter", Context.MODE_PRIVATE);
        // NOTE:  If the Nethunter app has not yet been run (to install these files), this won't do
        // anything.  For that reason it may be wise to do a full install of the files at boot as
        // well, but that doesn't happen now.  Easy to add, but merits some discussion if script
        // updates should be done at boot, at every app start (current practice), etc.
        if (!sharedpreferences.getBoolean(CHROOT_INSTALLED_TAG, false)) {
            // chroot not installed
            Log.d(TAG, "Nethunter chroot is not installed.");
            doing_action = "Nethunter chroot is not installed.";
            runBootServices = false;
            doNotification(getString(R.string.nokalichrootfound));
        } else if (!sharedpreferences.getBoolean(KaliServicesFragment.RUN_AT_BOOT, true)) {
            // USER DISABLED BOOT SERVICES
            Log.d(TAG, "USER DISABLED BOOT SERVICES");
            doing_action = "USER DISABLED BOOT SERVICES";
            runBootServices = false;
            doNotification("USER DISABLED BOOT SERVICES");
        }
        // check for DELETE_CHROOT_TAG pref & make sure default is NO
        if (ChrootManagerFragment.DELETE_CHROOT_TAG.equals(sharedpreferences.getString(ChrootManagerFragment.DELETE_CHROOT_TAG, ""))) {
            doing_action = "DELETE CHROOT";
            doNotification("Delete chroot request found.");
            runBootServices = false;
            // DELETE IS IN THE QUEUE -->  CHECK IF WE ARE UNMOUNTED BY COUNTING REFERENCES TO "KALI"
            // IN /proc/mounts
            String command = "if [ $(grep kali /proc/mounts -c) -ne 0 ];then echo 1; fi"; //check cmd
            final String _res;

            _res = x.RunAsRootOutput(command);

            if (_res.equals("1")) {
                Toast.makeText(getBaseContext(), getString(R.string.toastchrootmountedwarning), Toast.LENGTH_LONG).show();
                doNotification(getString(R.string.toastchrootmountedwarning));
            } else {
                doNotification(getString(R.string.toastdeletingchroot));
                Toast.makeText(getBaseContext(), getString(R.string.toastdeletingchroot), Toast.LENGTH_LONG).show();
                x.RunAsRootOutput("su -c 'rm -rf " + nh.NH_SYSTEM_PATH + "/*'");
                // remove the sp so we dont remove it again on next boot
                sharedpreferences.edit().remove(ChrootManagerFragment.DELETE_CHROOT_TAG).apply();
                sharedpreferences.edit().remove(ChrootManagerFragment.CHROOT_INSTALLED_TAG).apply();

                Toast.makeText(getBaseContext(), getString(R.string.toastdeletedchroot), Toast.LENGTH_LONG).show();
                doNotification(getString(R.string.toastdeletedchroot));

            }

        }

        if (ChrootManagerFragment.MIGRATE_CHROOT_TAG.equals(sharedpreferences.getString(ChrootManagerFragment.MIGRATE_CHROOT_TAG, ""))) {
            doing_action = "MIGRATE CHROOT";
            doNotification("Migrate chroot request found.");
            runBootServices = false;
            // CHECK IF WE ARE UNMOUNTED BY COUNTING REFERENCES TO "KALI"
            // IN /proc/mounts
            String command = "if [ $(grep kali /proc/mounts -c) -ne 0 ];then echo 1; fi"; //check cmd
            final String _res;

            _res = x.RunAsRootOutput(command);

            if (_res.equals("1")) {
                Toast.makeText(getBaseContext(), getString(R.string.toastchrootmountedwarning), Toast.LENGTH_LONG).show();
                doNotification(getString(R.string.toastchrootmountedwarning));
            } else {
                doNotification("Starting chroot migration...");
                Toast.makeText(getBaseContext(), getString(R.string.toastmigratingchroot), Toast.LENGTH_LONG).show();
                x.RunAsRootOutput("su -c 'mv " + nh.OLD_CHROOT_PATH + " " + nh.NH_SYSTEM_PATH + "'");
                Toast.makeText(getBaseContext(), getString(R.string.toastmigratedchroot), Toast.LENGTH_LONG).show();
                sharedpreferences.edit().remove(ChrootManagerFragment.MIGRATE_CHROOT_TAG).apply();
                doNotification(getString(R.string.toastmigratedchroot));
            }
        }

        if (userinit(runBootServices)) {
            Toast.makeText(getBaseContext(), "Boot end: ALL OK", Toast.LENGTH_SHORT).show();
//            doNotification("Boot ended. All fine. Action performed: " + doing_action + " OK");
        } else {
            if (!runBootServices) {
                Toast.makeText(getBaseContext(), "Not runing boot scripts. OK", Toast.LENGTH_SHORT).show();
//                doNotification("Boot ended. All fine. Action performed: " + doing_action + " OK");
            } else {
                doNotification("Boot ended. No busybox found!");
            }
        }
        // put change MAC addresses here.
        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // don't support binding for now.
    }

    private boolean userinit(Boolean ShouldRun) {
        if (!ShouldRun) {
            return false;
        }
        doing_action = "RUNNING BOOT SERVICES";
        // doNotification(TAG, "RUNNING BOOT SERVICES");
        // this duplicates the functionality of the userinit service, formerly in init.rc
        // These scripts will start up after the system is booted.
        // Put scripts in fileDir/scripts/etc/init.d/ and set execute permission.  Scripts should
        // start with a number and include a hashbang such as #!/system/bin/sh as the first line.
        ShellExecuter exe = new ShellExecuter();
        String busybox = nh.whichBusybox();
        if (!busybox.equals("")) {
            exe.RunAsRootOutput("rm -rf " + nh.CHROOT_PATH + "/tmp/.X1*"); // remove posible vnc locks (if the phone is rebooted with the vnc server running)
            // init.d
            String[] runner = {busybox + " run-parts " + nh.APP_INITD_PATH};
            exe.RunAsRoot(runner);
            Toast.makeText(getBaseContext(), getString(R.string.autorunningscripts), Toast.LENGTH_SHORT).show();
            return true;
        }
        Toast.makeText(getBaseContext(), getString(R.string.toastForNoBusybox), Toast.LENGTH_SHORT).show();
        doNotification(getString(R.string.toastForNoBusybox));
        return false;
    }
}
