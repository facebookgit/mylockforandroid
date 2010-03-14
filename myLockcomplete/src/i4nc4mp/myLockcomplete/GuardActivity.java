package i4nc4mp.myLockcomplete;

import java.util.GregorianCalendar;

import i4nc4mp.myLockcomplete.ManageKeyguard.LaunchOnKeyguardExit;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;


//Guarded mode - Lockscreen replacement that's designed to be used without pattern mode on
//the mediator for this mode does the pattern suppress with option for idle timeout
//it simply replaces the lockscreen, does not touch any wakeup rules

//the instant unlocking functionality will be released as standalone myLock basic
//automatically doing the secure lockscreen exit that was used by the original alpha 2c
//FIXME when it instant unlocks/dismisses at wakeup it causes big bugs in incoming or ignored calls.
//got unique odd behavior out of those two cases. FIX for the basic ver update.

//When it is time to exit, we start a one shot dismiss activity.
//The dismiss activity will load, wait 50 ms, then finish
//Here, we finish in the background immediately after requesting the dismiss activity

//For this lifecycle, we go dormant for any outside event
//such as incoming call ringing, alarm, handcent popup, etc.
//we detect going dormant by losing focus while paused.
//if focus loss occurs while not paused, it means the user is actively navigating out of the woken lockscreen

public class GuardActivity extends Activity {
    
	Handler serviceHandler;
	Task myTask = new Task();
    
    
/* Lifecycle flags */
    public boolean starting = true;//flag off after we successfully gain focus. flag on when we send task to back
    public boolean finishing = false;//flag on when an event causes unlock, back off when onStart comes in again (relocked)
    
    public boolean paused = false;
    
    public boolean idle = false;
    
    public boolean dormant = false;
    //special lifecycle phase- we are waiting in the background for outside event to return focus to us
    //an example of this is while a call is ringing. if aborted, focus comes back to us.
    //we toggle the flag off in the call abort broadcast reaction
    
    public boolean slideWakeup = false;
    //we will set this when we detect slideopen, only used with instant unlock (replacement for 2c ver)
    
//====Items in the default custom lockscreen    
    private Button mrewindIcon;
    private Button mplayIcon;
    private Button mpauseIcon;
    private Button mforwardIcon;
    
    public TextView curhour;
    public TextView curmin;
    
    public TextView batt;
	
	
        //very very complicated business.
        @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        
        updateLayout();
        
        curhour = (TextView) findViewById(R.id.hourText);
        
        curmin = (TextView) findViewById(R.id.minText);
        
        batt = (TextView) findViewById(R.id.batt);
        
       updateClock();
        
        mrewindIcon = (Button) findViewById(R.id.PrevButton); 
        
        mrewindIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
             Intent intent;
             intent = new Intent("com.android.music.musicservicecommand.previous");
             getApplicationContext().sendBroadcast(intent);
             }
          });
 
        mplayIcon = (Button) findViewById(R.id.PlayToggle); 
 
        mplayIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
             Intent intent;
             intent = new Intent("com.android.music.musicservicecommand.togglepause");
             getApplicationContext().sendBroadcast(intent);
             /*if (!am.isMusicActive()) {
                 mpauseIcon.setVisibility(View.VISIBLE);
                 mplayIcon.setVisibility(View.GONE);
                 }*/
             }
          });
 
        /*mpauseIcon = (ImageButton) findViewById(R.id.pauseIcon); 
 
        mpauseIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
             Intent intent;
             intent = new Intent("com.android.music.musicservicecommand.togglepause");
             getBaseContext().sendBroadcast(intent);
             if (am.isMusicActive()) {
                 mplayIcon.setVisibility(View.VISIBLE);
                 mpauseIcon.setVisibility(View.GONE);
                 }
             }
          });*/
 
        mforwardIcon = (Button) findViewById(R.id.NextButton); 
 
        mforwardIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
             Intent intent;
             intent = new Intent("com.android.music.musicservicecommand.next");
             getApplicationContext().sendBroadcast(intent);
             }
          });
        
        IntentFilter onfilter = new IntentFilter (Intent.ACTION_SCREEN_ON);
		registerReceiver(screenon, onfilter);
        
        IntentFilter callbegin = new IntentFilter ("i4nc4mp.myLockcomplete.lifecycle.CALL_START");
        registerReceiver(callStarted, callbegin);  
        
        IntentFilter callabort = new IntentFilter ("i4nc4mp.myLockcomplete.lifecycle.CALL_ABORT");
        registerReceiver(callAborted, callabort);
        
		IntentFilter idleFinish = new IntentFilter ("i4nc4mp.myLockcomplete.lifecycle.IDLE_TIMEOUT");
		registerReceiver(idleExit, idleFinish);
		
        serviceHandler = new Handler();
    }
        
        public void updateClock() {
        	GregorianCalendar Calendar = new GregorianCalendar();         
            
        	int mHour = Calendar.get(GregorianCalendar.HOUR_OF_DAY);
        	int mMin = Calendar.get(GregorianCalendar.MINUTE);
        	
        	String hour = new String("");
        	String min = new String("");
        	
            if (mHour <10) hour = hour + "0";
            hour = hour + mHour;
            
            if (mMin <10) min = min + "0";
            min = min + mMin;
            
            curhour.setText(hour);
            curmin.setText(min);
            
            
            //update battery as it is also a form of time passing
            
            SharedPreferences settings = getSharedPreferences("myLock", 0);
            int battlevel = settings.getInt("BattLevel", 0);
            
            batt.setText(battlevel + "%");
            
            
        }
        
        
               
    protected View inflateView(LayoutInflater inflater) {
        return inflater.inflate(R.layout.lockactivity, null);
    }

    private void updateLayout() {
        LayoutInflater inflater = LayoutInflater.from(this);

        setContentView(inflateView(inflater));
    }
        
    @Override
    public void onBackPressed() {
    	//Back will cause unlock
    	StartDismiss(getApplicationContext());
    	finishing = true;
    	//to close self from the background, we will call finish in onStop
    }
    
    
    BroadcastReceiver screenon = new BroadcastReceiver() {
         	
        public static final String Screenon = "android.intent.action.SCREEN_ON";

        @Override
        public void onReceive(Context context, Intent intent) {
                if (!intent.getAction().equals(Screenon)) return;
                
        //if (hasWindowFocus()) onBackPressed();
        //if user turns on auto-exit (simple mode) it can just call back press at screen on
                
        //complication with slider open, for some reason we gain focus before the screen on
        //theres a major lag in screen waking up when you slide open, for some reason
                
        return;//avoid unresponsive receiver error outcome
             
}};
    
    BroadcastReceiver callStarted = new BroadcastReceiver() {
    	@Override
        public void onReceive(Context context, Intent intent) {
    	if (!intent.getAction().equals("i4nc4mp.myLockcomplete.lifecycle.CALL_START")) return;
    	
    	//we are going to be dormant while this happens, therefore we need to force finish
    	StopCallback();
    	finish();
    	
    	}};
       	
    BroadcastReceiver callAborted = new BroadcastReceiver() {
       	@Override
           public void onReceive(Context context, Intent intent) {
      	if (!intent.getAction().equals("i4nc4mp.myLockcomplete.lifecycle.CALL_ABORT")) return;
       		//do anything special we need to do right after the call abort
       		//we might not even need this broadcast in this mode
            	
      	}};
    

	BroadcastReceiver idleExit = new BroadcastReceiver() {
	@Override
    public void onReceive(Context context, Intent intent) {
	if (!intent.getAction().equals("i4nc4mp.myLockcomplete.lifecycle.IDLE_TIMEOUT")) return;
	
	finishing = true;
	idle = true;
	
	Log.v("exit intent received","calling finish");
	finish();//we will still have focus because this comes from the mediator as a wake event
	}};

	class Task implements Runnable {
	public void run() {
		
		ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
            public void LaunchOnKeyguardExitSuccess() {
               Log.v("doExit", "This is the exit callback");
               StopCallback();
               finish();
                }});
	}}
	
	/*
	@Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
     	if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
     		//this means that a config change happened and the keyboard is open.
     		if (starting) {
     			Log.v("slide-open lock","aborting handling, slide was opened before this lock");
     		}
     		else {
     		ManageKeyguard.disableKeyguard(getApplicationContext());
     		     		
        	slideWakeup = true;
     		}
     	}
     	else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES)
     		Log.v("slide closed","lockscreen activity got the config change from background");    	
    }*/
    
	@Override
    protected void onStop() {
        super.onStop();
                
        if (finishing) {
        	Log.v("lock stop","we have been unlocked by a user exit request");
        }
        else if (paused) {
        	if (hasWindowFocus()) {
        
        	//stop is called, we were already paused, and still have focus
        	//this means something is about to take focus, we should go dormant
        	dormant = true;
        	Log.v("lock stop","detected external event about to take focus, setting dormant");
        	}
            else if (!hasWindowFocus()) {
        	//we got paused, lost focus, then finally stopped
        	//this only happens if user is navigating out via notif, popup, or home key shortcuts
        	Log.v("lock stop","onStop is telling mediator we have been unlocked by user navigation");       	
            }
        }
        else Log.v("unexpected onStop","lockscreen was stopped for unknown reason");
        
        if (finishing) {
        	StopCallback();
        	finish();
        }
        
        //starting = true;//this way if we get brought back we'll be aware of it
        //changed this activity to always finish when exited. never hangs out in background
        
        //StopCallback();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	
    	paused = true;
    	
    	if (!starting && !hasWindowFocus()) {
    		//case: we yielded focus to something but didn't pause. Example: notif panel
    		//pause in this instance means something else is launching, that is about to try to stop us
    		//so we need to exit now, as it is a user nav, not a dormancy event
    		Log.v("navigation exit","got paused without focus, starting dismiss sequence");
    		
    		
    		finishing = true;
    		ManageKeyguard.disableKeyguard(getApplicationContext());
    		//OS doesn't care about re-enable call here, but it would if it was a home key exit
    		StartDismiss(getApplicationContext());
    	}
    	else Log.v("lock paused","normal pause - we still have focus");    	
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	Log.v("lock resume","resuming, focus is " + hasWindowFocus());
    	paused = false;
    	
    	updateClock();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
                
       serviceHandler.removeCallbacks(myTask);
       serviceHandler = null;
       
       unregisterReceiver(screenon);
       unregisterReceiver(callStarted);
       unregisterReceiver(callAborted);
       unregisterReceiver(idleExit);
    	
       Log.v("destroy Guard","Destroying");
    }
    
    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
    	if (hasFocus) {
    		Log.v("focus change","we have gained focus");
    		//Catch first focus gain after onStart here.
    		//this allows us to know if we actually got as far as having focus (expected but bug sometimes prevents
    		if (starting) {
    			starting = false;
    			//set our own lifecycle reference now that we know we started and got focus properly
    			
    			//tell mediator it is no longer waiting for us to start up
    			StartCallback();
    		}
    		else if (dormant) {
    			Log.v("regained","we are no longer dormant");
    			dormant = false;
    		}
    	}
    	else {    		    		   		
    		if (!finishing && paused) {
   				if (!dormant) {
   					Log.v("home key exit","launching full secure exit");
   					   						
   					ManageKeyguard.disableKeyguard(getApplicationContext());
   					serviceHandler.postDelayed(myTask, 50);
   						
   					//Intent i = new Intent("i4nc4mp.myLockcomplete.lifecycle.HOMEKEY_UNLOCK");
   			        //getApplicationContext().sendBroadcast(i);
  				}
   				else Log.v("focus lost while paused","external event has taken focus");
    		}
    		else if (!paused) Log.v("focus yielded while active","about to exit through notif nav");
    	}
    }
    
    protected void onStart() {
    	super.onStart();
    	Log.v("lockscreen start success","setting flags");
    	
    	if (finishing) {
    		finishing = false;
    		//since we are sometimes being brought back, safe to ensure flags are like at creation
    	}
    }
    
    public void StartCallback() {
    	Intent i = new Intent("i4nc4mp.myLockcomplete.lifecycle.LOCKSCREEN_PRIMED");
        getApplicationContext().sendBroadcast(i);
    }
    
    public void StopCallback() {
    	Intent i = new Intent("i4nc4mp.myLockcomplete.lifecycle.LOCKSCREEN_EXITED");
        getApplicationContext().sendBroadcast(i);
    }
    
    public void StartDismiss(Context context) {
        
        
        Class w = DismissActivity.class; 
                      
        Intent dismiss = new Intent(context, w);
        dismiss.setFlags(//Intent.FLAG_ACTIVITY_NEW_TASK//For some reason it requires this even though we're already an activity
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION//Just helps avoid conflicting with other important notifications
                        | Intent.FLAG_ACTIVITY_NO_HISTORY//Ensures the activity WILL be finished after the one time use
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        
        startActivity(dismiss);
        //Here, we also need to initiate finish, as our focus loss is treating it as dormancy
        finish();
        //works like a charm for user initiated exit.
    }
    
    
    //Only will be used for the update to the first market beta to make it a full "Simple version" release
    //TODO it is possible the dismiss method is better all around.
    public void DoExit(Context context) {//try the alpha keyguard manager secure exit
        
        //ManageKeyguard.initialize(context);
        //PowerManager pm = (PowerManager) getSystemService (Context.POWER_SERVICE); 
        //pm.userActivity(SystemClock.uptimeMillis(), false);
        //ensure it will be awake
        
        if (!slideWakeup) ManageKeyguard.disableKeyguard(getApplicationContext());
        else {
        	slideWakeup = false;
        	Log.v("completing slider open wake","about to try secure exit");
        }
        
        serviceHandler.postDelayed(myTask, 50);
        
                  
    }        
}