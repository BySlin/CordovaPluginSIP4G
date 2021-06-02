package com.sip.linphone;
/*
LinphoneMiniManager.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.view.SurfaceView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.linphone.core.Address;
import org.linphone.core.AudioDevice;
import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Conference;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.Event;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.InfoMessage;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.RegistrationState;
import org.linphone.core.SubscriptionState;
import org.linphone.core.VersionUpdateCheckResult;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniManager implements CoreListener {
  private static final String TAG = "LM_MNGR";
  @SuppressLint("StaticFieldLeak")
  public static LinphoneMiniManager mInstance;
  @SuppressLint("StaticFieldLeak")
  public static Context mContext;
  public static Core mCore;
  //public static LinphonePreferences mPrefs;
  public static Timer mTimer;
  @SuppressLint("StaticFieldLeak")
  public static SurfaceView mCaptureView;
  public CallbackContext mCallbackContext;
  public CallbackContext mLoginCallbackContext;

  public LinphoneMiniManager(Context c) {
    mContext = c;
    Factory.instance().setDebugMode(true, "Linphone Mini");
    //mPrefs = LinphonePreferences.instance();


    try {

      String basePath = mContext.getFilesDir().getAbsolutePath();
      copyAssetsFromPackage(basePath);
      mCore = Factory.instance().createCore(basePath + "/.linphonerc", basePath + "/linphonerc", mContext);
      initCoreValues(basePath);

      setUserAgent();
      setFrontCamAsDefault();
      startIterate();
      mInstance = this;
      mCore.setNetworkReachable(true); // Let's assume it's true
      mCore.addListener(this);
      mCaptureView = new SurfaceView(mContext);
      mCore.start();

    } catch (IOException e) {
      Log.e("Error initializing Linphone", e.getMessage());

    }
  }

  public Core getLc() {
    return mCore;
  }

  public static LinphoneMiniManager getInstance() {
    return mInstance;
  }

  public void destroy() {
    try {
      mTimer.cancel();
      mCore.stopRinging();
      mCore.stopConferenceRecording();
      mCore.stopDtmf();
      mCore.stopAsync();
      mCore.stopEchoTester();
    } catch (RuntimeException ignored) {
    } finally {
      mCore = null;
      mInstance = null;
    }
  }

  private void startIterate() {
    TimerTask lTask = new TimerTask() {
      @Override
      public void run() {
        mCore.iterate();
      }
    };

    mTimer = new Timer("LinphoneMini scheduler");
    mTimer.schedule(lTask, 0, 20);
  }


  private void setUserAgent() {
    try {
      String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
      if (versionName == null) {
        versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
      }
      mCore.setUserAgent("LinphoneMiniAndroid", versionName);
    } catch (NameNotFoundException ignored) {
    }
  }

  private void setFrontCamAsDefault() {
    int camId = 0;
    AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
    for (AndroidCamera androidCamera : cameras) {
      if (androidCamera.frontFacing)
        camId = androidCamera.id;
    }
    mCore.setVideoDevice("" + camId);
  }

  private void copyAssetsFromPackage(String basePath) throws IOException {
    String package_name = mContext.getPackageName();
    Resources resources = mContext.getResources();

    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("oldphone_mono", "raw", package_name), basePath + "/oldphone_mono.wav");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("ringback", "raw", package_name), basePath + "/ringback.wav");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("toy_mono", "raw", package_name), basePath + "/toy_mono.wav");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("linphonerc_default", "raw", package_name), basePath + "/.linphonerc");
    LinphoneMiniUtils.copyFromPackage(mContext, resources.getIdentifier("linphonerc_factory", "raw", package_name), new File(basePath + "/linphonerc").getName());
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("lpconfig", "raw", package_name), basePath + "/lpconfig.xsd");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("rootca", "raw", package_name), basePath + "/rootca.pem");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("vcard_grammar", "raw", package_name), basePath + "/vcard_grammar.pem");
    LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("cpim_grammar", "raw", package_name), basePath + "/cpim_grammar.pem");
  }

  private void initCoreValues(String basePath) {
//		mCore.setContext(mContext);
    mCore.setRing(null);
    mCore.setRootCa(basePath + "/rootca.pem");
    mCore.setPlayFile(basePath + "/toy_mono.wav");
    mCore.setCallLogsDatabasePath(basePath + "/linphone-history.db");

//		int availableCores = Runtime.getRuntime().availableProcessors();
//		mCore.getConfig(availableCores);
  }

  public void newOutgoingCall(String to, String displayName) {
    Address lAddress = mCore.interpretUrl(to);
    ProxyConfig lpc = mCore.getDefaultProxyConfig();

    if (lpc != null && lAddress.asStringUriOnly().equals(lpc.getDomain())) {
      return;
    }

    lAddress.setDisplayName(displayName);

    if (mCore.isNetworkReachable()) {
      CallParams params = mCore.createCallParams(mCore.getCurrentCall());
      params.enableVideo(false);
      mCore.inviteAddressWithParams(lAddress, params);
    } else {
      Log.e("Error: Network unreachable");
    }

  }

  public boolean setLowBandwidth(boolean lowBandwidth) {
    try {
      CallParams params = mCore.getCurrentCall().getCurrentParams();
      params.enableLowBandwidth(lowBandwidth);
      mCore.getCurrentCall().setParams(params);
      return lowBandwidth;
    } catch (Exception e) {
      return false;
    }

  }

  public boolean setPlaybackGainDb(String value) {
    try {
      mCore.setPlaybackGainDb(Float.parseFloat(value));
      return true;
    } catch (Exception e) {
      android.util.Log.d(TAG, "setPlaybackGainDb " + e.toString());
      return false;
    }
  }

  public boolean setMicGainDb(String value) {

    try {

      mCore.setMicGainDb(Float.parseFloat(value));
      return true;
    } catch (Exception e) {
      android.util.Log.d(TAG, "setMicGain " + e.toString());
      return false;
    }
  }

  public boolean setMicrophoneVolumeGain(String value) {
    try {
      mCore.getCurrentCall().setMicrophoneVolumeGain(((Float.parseFloat(value)) / 100));
      return true;
    } catch (Exception e) {
      android.util.Log.d(TAG, "setMicrophoneVolumeGain " + e.toString());
      return false;
    }
  }

  public void terminateCall() {
    if (mCore.inCall()) {
      Call c = mCore.getCurrentCall();
      if (c != null) c.terminate();
    }
  }

  public boolean toggleEnableCamera() {
    if (mCore.inCall()) {
      boolean enabled = !mCore.getCurrentCall().cameraEnabled();
      enableCamera(mCore.getCurrentCall(), enabled);
      return enabled;
    }
    return false;
  }

  public boolean toggleEnableSpeaker(boolean speaker) {

    final Context context = mContext;
    final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    if (speaker) {
      audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
      audioManager.setSpeakerphoneOn(true);


			/*int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
			audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);*/

    } else {
      audioManager.setMode(AudioManager.MODE_NORMAL);
      audioManager.setSpeakerphoneOn(false);
    }

    return speaker;

  }


  public boolean toggleMute() {
    if (mCore.inCall()) {
      boolean enabled = !mCore.getDisableRecordOnMute();
      mCore.setDisableRecordOnMute(enabled);
      return enabled;
    }
    return false;
  }

  public void enableCamera(Call call, boolean enable) {
    if (call != null) {
      call.enableCamera(enable);
    }
  }

  public void sendDtmf(char number) {
    mCore.getCurrentCall().sendDtmf(number);
  }

  public void updateCall() {
    Core lc = mCore;
    Call lCall = lc.getCurrentCall();
    if (lCall == null) {
      Log.e("Trying to updateCall while not in call: doing nothing");
    } else {
      CallParams params = lCall.getParams();
      lc.getCurrentCall().setParams(params);
    }
  }


  public void listenCall(CallbackContext callbackContext) {
    mCallbackContext = callbackContext;
  }

  public void acceptCall(CallbackContext callbackContext) {
    mCallbackContext = callbackContext;
    Call call = mCore.getCurrentCall();
    if (call != null) {
      call.accept();
    }

  }

  public void call(String address, String displayName, CallbackContext callbackContext) {

    mCallbackContext = callbackContext;
    newOutgoingCall(address, displayName);
  }

  public void hangup(CallbackContext callbackContext) {
    terminateCall();
  }

  public void login(String username, String password, String domain, String realm, int regExpirationTimeout, CallbackContext callbackContext) {
    Factory lcFactory = Factory.instance();

    Address address = lcFactory.createAddress("sip:" + username + "@" + realm);
    username = address.getUsername();
    //domain = address.getDomain();
    if (password != null) {
      mCore.addAuthInfo(lcFactory.createAuthInfo(username, null, password, null, realm, domain));
    }


//			ProxyConfig proxyCfg = mCore.createProxyConfig("sip:" + username + "@" + domain, domain, (String)null, true);
    ProxyConfig proxyCfg = mCore.createProxyConfig();
    proxyCfg.edit();
    proxyCfg.setIdentityAddress(address);
    proxyCfg.setServerAddr(domain);
    proxyCfg.setExpires(regExpirationTimeout);
    proxyCfg.setRealm(realm);
    proxyCfg.done();


    mCore.addProxyConfig(proxyCfg);
    mCore.setDefaultProxyConfig(proxyCfg);


    proxyCfg.enableRegister(true);
    mLoginCallbackContext = callbackContext;

    mCore.enableEchoCancellation(true);
    mCore.enableEchoLimiter(true);
  }

  @Override
  public void onGlobalStateChanged(Core core, GlobalState globalState, String s) {
    android.util.Log.d(TAG, "Global state changed");
    android.util.Log.d(TAG, globalState.name());
    android.util.Log.d(TAG, s);
  }


  public PluginResult callbacCustom(String message) {
    PluginResult result = new PluginResult(PluginResult.Status.OK, message);
    result.setKeepCallback(true);
    return result;
  }

  @Override
  public void onRegistrationStateChanged(Core core, ProxyConfig proxyConfig, RegistrationState registrationState, String s) {
    if (registrationState == RegistrationState.Ok) {
      mLoginCallbackContext.sendPluginResult(callbacCustom("Ok"));
    } else if (registrationState == RegistrationState.Failed) {
      mLoginCallbackContext.sendPluginResult(callbacCustom("RegistrationFailed:: " + s));
    } else if (registrationState == RegistrationState.Progress) {
      mLoginCallbackContext.sendPluginResult(callbacCustom("Trying"));
    }
  }

  @Override
  public void onCallStateChanged(Core core, Call call, State state, String s) {
    if (state == State.Connected) {
      toggleEnableSpeaker(true);
      mCallbackContext.sendPluginResult(callbacCustom("Connected"));
    } else if (state == State.IncomingReceived) {
      mCallbackContext.sendPluginResult(callbacCustom("Incoming"));
    } else if (state == State.End) {
      toggleEnableSpeaker(false);
      mCallbackContext.sendPluginResult(callbacCustom("End"));
    } else if (state == State.Error) {
      toggleEnableSpeaker(false);
      mCallbackContext.sendPluginResult(callbacCustom("Error"));
    }
    Log.d("Call state: " + state + "(" + s + ")");
  }

  /**
   * Callback prototype telling that the audio device for at least one call has<br/>
   * changed. <br/>
   * <br/>
   *
   * @param core        LinphoneCore object   <br/>
   * @param audioDevice the newly used LinphoneAudioDevice object   <br/>
   */
  @Override
  public void onAudioDeviceChanged(Core core, AudioDevice audioDevice) {

  }

  @Override
  public void onNotifyPresenceReceived(Core core, Friend friend) {

  }

  @Override
  public void onNotifyPresenceReceivedForUriOrTel(Core core, Friend friend, String s, PresenceModel presenceModel) {

  }

  @Override
  public void onNewSubscriptionRequested(Core core, Friend friend, String s) {

  }

  @Override
  public void onAuthenticationRequested(Core core, AuthInfo authInfo, AuthMethod authMethod) {
    android.util.Log.d(TAG, "Authentication requested");
  }

  @Override
  public void onCallLogUpdated(Core core, CallLog callLog) {
    android.util.Log.d(TAG, "Call log updated" + callLog.toStr());
  }

  @Override
  public void onMessageReceived(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

  }

  @Override
  public void onChatRoomRead(Core core, ChatRoom chatRoom) {

  }

  @Override
  public void onChatRoomSubjectChanged(Core core, ChatRoom chatRoom) {

  }

  @Override
  public void onMessageReceivedUnableDecrypt(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

  }


  @Override
  public void onMessageSent(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

  }


  @Override
  public void onIsComposingReceived(Core core, ChatRoom chatRoom) {

  }

  @Override
  public void onDtmfReceived(Core core, Call call, int i) {
    android.util.Log.d(TAG, "DTMF RECEIVED");
  }

  @Override
  public void onReferReceived(Core core, String s) {

  }

  @Override
  public void onCallEncryptionChanged(Core core, Call call, boolean b, String s) {

  }

  /**
   * Callback to notify the callid of a call has been updated. <br/>
   * <br/>
   * This is done typically when a call retry. <br/>
   *
   * @param core           the LinphoneCore   <br/>
   * @param previousCallId the previous callid.   <br/>
   * @param currentCallId  the new callid.   <br/>
   */
  @Override
  public void onCallIdUpdated(Core core, String previousCallId, String currentCallId) {

  }

  @Override
  public void onTransferStateChanged(Core core, Call call, State state) {

  }

  /**
   * Callback prototype telling that a LinphoneChatRoom ephemeral message has<br/>
   * expired. <br/>
   * <br/>
   *
   * @param core     LinphoneCore object   <br/>
   * @param chatRoom The LinphoneChatRoom object for which a message has expired.   <br/>
   */
  @Override
  public void onChatRoomEphemeralMessageDeleted(Core core, ChatRoom chatRoom) {

  }

  @Override
  public void onBuddyInfoUpdated(Core core, Friend friend) {

  }

  @Override
  public void onCallStatsUpdated(Core core, Call call, CallStats callStats) {
    android.util.Log.d(TAG, "Call stats updated:: Download bandwidth :: " + callStats.getDownloadBandwidth());
  }

  @Override
  public void onInfoReceived(Core core, Call call, InfoMessage infoMessage) {
    android.util.Log.d(TAG, "Info message received :: " + infoMessage.getContent().getStringBuffer());
  }

  @Override
  public void onSubscriptionStateChanged(Core core, Event event, SubscriptionState subscriptionState) {
    android.util.Log.d(TAG, "Subscription state changed :: " + subscriptionState.name());
  }

  @Override
  public void onNotifyReceived(Core core, Event event, String s, Content content) {

  }

  @Override
  public void onSubscribeReceived(Core core, Event event, String s, Content content) {

  }

  /**
   * Callback prototype telling that a LinphoneConference state has changed. <br/>
   * <br/>
   *
   * @param core       LinphoneCore object   <br/>
   * @param conference The LinphoneConference object for which the state has changed<br/>
   *                   <br/>
   * @param state      the current LinphoneChatRoomState <br/>
   */
  @Override
  public void onConferenceStateChanged(Core core, Conference conference, Conference.State state) {

  }

  @Override
  public void onPublishStateChanged(Core core, Event event, PublishState publishState) {

  }

  @Override
  public void onConfiguringStatus(Core core, ConfiguringState configuringState, String s) {

  }

  @Override
  public void onNetworkReachable(Core core, boolean b) {
    android.util.Log.d(TAG, "Network reachable??" + b);
  }

  @Override
  public void onLogCollectionUploadStateChanged(Core core, Core.LogCollectionUploadState logCollectionUploadState, String s) {

  }

  /**
   * Callback prototype telling the audio devices list has been updated. <br/>
   * <br/>
   * Either a new device is available or a previously available device isn't<br/>
   * anymore. You can call linphone_core_get_audio_devices to get the new list. <br/>
   *
   * @param core LinphoneCore object   <br/>
   */
  @Override
  public void onAudioDevicesListUpdated(Core core) {

  }

  @Override
  public void onLogCollectionUploadProgressIndication(Core core, int i, int i1) {

  }

  @Override
  public void onFriendListCreated(Core core, FriendList friendList) {

  }

  @Override
  public void onFriendListRemoved(Core core, FriendList friendList) {

  }

  @Override
  public void onCallCreated(Core core, Call call) {

  }

  @Override
  public void onVersionUpdateCheckResultReceived(Core core, VersionUpdateCheckResult versionUpdateCheckResult, String s, String s1) {

  }

  /**
   * Callback prototype telling the last call has ended<br/>
   * (#LinphoneCore.get_calls_nb() returns 0) <br/>
   * <br/>
   *
   * @param core LinphoneCore object   <br/>
   */
  @Override
  public void onLastCallEnded(Core core) {

  }

  @Override
  public void onChatRoomStateChanged(Core core, ChatRoom chatRoom, ChatRoom.State state) {

  }

  /**
   * Callback prototype telling that an Instant Message Encryption Engine user<br/>
   * registered on the server with or without success. <br/>
   * <br/>
   *
   * @param core   LinphoneCore object   <br/>
   * @param status the return status of the registration action. <br/>
   * @param userId the userId published on the encryption engine server   <br/>
   * @param info   information about failure   <br/>
   */
  @Override
  public void onImeeUserRegistration(Core core, boolean status, String userId, String info) {

  }

  @Override
  public void onQrcodeFound(Core core, String s) {

  }

  @Override
  public void onEcCalibrationResult(Core core, EcCalibratorStatus ecCalibratorStatus, int i) {

  }

  @Override
  public void onEcCalibrationAudioInit(Core core) {

  }

  /**
   * Callback prototype telling a call has started (incoming or outgoing) while<br/>
   * there was no other call. <br/>
   * <br/>
   *
   * @param core LinphoneCore object   <br/>
   */
  @Override
  public void onFirstCallStarted(Core core) {

  }

  @Override
  public void onEcCalibrationAudioUninit(Core core) {

  }
}
