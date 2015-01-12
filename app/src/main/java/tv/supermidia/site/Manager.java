package tv.supermidia.site;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class Manager extends Service {
    public static final String TAG = "SUPERMIDIA.TV:Manager";
    public static final String REQUEST_KILL_SITE = "tv.supermidia.site.request-kill-site";
    public static final String EVENT_UP = "tv.supermidia.site.event-manager-up";
    public static final String EVENT_DOWN = "tv.supermidia.site.event-manager-down";
    private WifiManager mWifiManager;
    private Thread mThreadRefresh;
    private BroadcastReceiver mReceiver;

    private boolean siteUp = false;

    private boolean offlineUp = false;
    private CountDownTimer siteUpCountdown;
    private CountDownTimer offlineUpCountdown;

    public Manager() {
        super();
        //mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        final Context parent = this;
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                //Log.d(TAG, "Received: " + action);
                if (action.compareTo(Site.EVENT_UP) == 0 || action.compareTo(Site.EVENT_ALIVE) == 0) {
                    setSiteUp(true);
                } else if (action.compareTo(Site.EVENT_DOWN) == 0) {
                    setSiteUp(false);
                }else if (action.compareTo(Offline.EVENT_UP) == 0 || action.compareTo(Offline.EVENT_ALIVE) == 0) {
                    setOfflineUp(true);
                }  else if (action.compareTo(Offline.EVENT_DOWN) == 0) {
                    setOfflineUp(false);
                }
            }
        };
        startThreads();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Toast.makeText(this, TAG + " service starting", Toast.LENGTH_SHORT).show();
        sendBroadcast(new Intent(EVENT_UP));

        if (mReceiver != null) {
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_ALIVE));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_UP));
            registerReceiver(mReceiver, new IntentFilter(Site.EVENT_DOWN));

            registerReceiver(mReceiver, new IntentFilter(Offline.EVENT_ALIVE));
            registerReceiver(mReceiver, new IntentFilter(Offline.EVENT_UP));
            registerReceiver(mReceiver, new IntentFilter(Offline.EVENT_DOWN));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, TAG + " service starting", Toast.LENGTH_SHORT).show();
        stopThreads();
        sendBroadcast(new Intent(EVENT_DOWN));

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    private void startSite() {
        //Log.d(TAG, "Staring site");

        Intent intent = new Intent(this, Site.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void stopSite() {
        Intent i = new Intent(REQUEST_KILL_SITE);
        sendBroadcast(i);
    }

    private void startOffline() {
        Intent intent = new Intent(this, Offline.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean hasWifi() {
        if (mWifiManager == null) {
            /* unknow state, assume offline */
            return false;
        }
        return mWifiManager.isWifiEnabled();
    }
    private void startThreads() {
        if (mThreadRefresh != null) {
            return;
        }
        final Context parent = this;
        mThreadRefresh = new Thread() {
            @Override
            public void run() {
                long secondsRunning = 0;
                try {
                    Log.d(TAG, "thread  for refresh is started");
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        /* limit it to 24 hours */
                        secondsRunning = secondsRunning == 24 * 60 * 60?0:secondsRunning;
                        secondsRunning++;
                        /* finish the site activity and start it again*/
                        if (hasWifi()) {
                            if (! isSiteUp() ) {
                                startSite();
                            }
                            /* restart site every 2 hours  */
                            if (secondsRunning % (60 * 60 * 2) == 0) {
                                /* kill site */
                                stopSite();
                            }
                            continue;
                        }

                        /* has no wifi */
                        if (isSiteUp()) {
                            /* continue to show site */
                            continue;
                        }

                        /* no wifi and no site, :-( */
                        if (! isOfflineUp()) {
                            startOffline();
                        }

                    }
                } catch(InterruptedException e){}
                Log.d(TAG, "thread for refresh was stopped");
            }
        };
        mThreadRefresh.start();
    }



    private void stopThreads() {
        if (mThreadRefresh == null) {
            return;
        }
        mThreadRefresh.interrupt();
        while (mThreadRefresh != null) {
            try {
                mThreadRefresh.join();
                mThreadRefresh = null;
            } catch (InterruptedException e) {
            }
        }

    }


    public synchronized boolean isSiteUp() {
        return siteUp;
    }

    public synchronized void setSiteUp(boolean siteUp) {
        if (this.siteUp != siteUp) {
            Log.d(TAG, "siteUp is now " + siteUp);
        }
        this.siteUp = siteUp;

        if (siteUpCountdown != null) {
            siteUpCountdown.cancel();
            siteUpCountdown = null;
        }

        if (siteUp == true) {
            siteUpCountdown = new CountDownTimer(5000, 5000) {
                    public void onTick(long millisUntilFinished) {
                    }

                    public void onFinish() {
                        siteUpCountdown = null;
                        setSiteUp(false);
                    }
                };
            siteUpCountdown.start();
        }
    }

    public synchronized boolean isOfflineUp() {
        return offlineUp;
    }

    public synchronized void setOfflineUp(boolean offlineUp) {
        if (this.offlineUp != offlineUp) {
            Log.d(TAG, "siteUp is now " + siteUp);
        }
        this.offlineUp = offlineUp;

        if (offlineUpCountdown != null) {
            offlineUpCountdown.cancel();
            offlineUpCountdown = null;
        }

        if (offlineUp == true) {
            offlineUpCountdown = new CountDownTimer(5000, 5000) {
                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    offlineUpCountdown = null;
                    setOfflineUp(false);
                }
            };
            offlineUpCountdown.start();
        }

    }

}
