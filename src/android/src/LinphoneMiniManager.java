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
import android.media.AudioManager;
import android.view.SurfaceView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.GlobalState;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;


/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniManager extends CoreListenerStub {
  private static final String TAG = "LM_MNGR";
  @SuppressLint("StaticFieldLeak")
  public static LinphoneMiniManager mInstance;
  @SuppressLint("StaticFieldLeak")
  public static Context mContext;
  public static Core mCore;
  @SuppressLint("StaticFieldLeak")
  public static SurfaceView mCaptureView;
  public CallbackContext mCallbackContext;
  public CallbackContext mLoginCallbackContext;

  public LinphoneMiniManager(Context c) {
    mContext = c;
    Factory.instance().setDebugMode(true, "Linphone Mini");
    mCore = Factory.instance().createCore(null, null, mContext);

    setUserAgent();
    setFrontCamAsDefault();
    mInstance = this;
    mCore.setNetworkReachable(true);
    mCore.addListener(this);
    mCore.enableEchoCancellation(true);
    mCore.enableEchoLimiter(true);
    mCore.setMaxCalls(1);
    mCaptureView = new SurfaceView(mContext);
  }

  public Core getLc() {
    return mCore;
  }

  public static LinphoneMiniManager getInstance() {
    return mInstance;
  }

  public void destroy() {
    try {
      mCore.stopRinging();
      mCore.stopConferenceRecording();
      mCore.stopDtmf();
      mCore.stopAsync();
      mCore.stopEchoTester();
      mCore.stop();
    } catch (RuntimeException ignored) {
    } finally {
      mCore = null;
      mInstance = null;
    }
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

  public void newOutgoingCall(String to, String displayName) {
    Address remoteAddress = Factory.instance().createAddress(to);
    remoteAddress.setDisplayName(displayName);

    if (mCore.isNetworkReachable()) {
      CallParams params = mCore.createCallParams(null);
      params.enableVideo(false);
      mCore.inviteAddressWithParams(remoteAddress, params);
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
      audioManager.setMode(AudioManager.MODE_NORMAL);
      audioManager.setSpeakerphoneOn(true);
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

  public void acceptCall() {
    Call call = mCore.getCurrentCall();
    if (call != null) {
      call.accept();
    }
  }

  public void call(String address, String displayName) {
    newOutgoingCall(address, displayName);
  }

  public void hangup() {
    terminateCall();
  }

  /**
   * 登陆sip
   *
   * @param username
   * @param password
   * @param domain
   * @param realm
   * @param regExpirationTimeout
   * @param callbackContext
   */
  public void login(String username, String password, String domain, String realm, int regExpirationTimeout, CallbackContext callbackContext) {
    Account defaultAccount = mCore.getDefaultAccount();
    if (defaultAccount != null) {
      mCore.removeAccount(defaultAccount);
    }

    Factory lcFactory = Factory.instance();

    AuthInfo authInfo = lcFactory.createAuthInfo(username, null, password, null, null, domain);
    AccountParams params = mCore.createAccountParams();
    Address identity = lcFactory.createAddress("sip:" + username + "@" + domain);
    params.setIdentityAddress(identity);
    Address address = Factory.instance().createAddress("sip:" + domain);
    address.setTransport(TransportType.Udp);
    params.setExpires(regExpirationTimeout);
    params.setServerAddress(address);
    params.setRegisterEnabled(true);

    Account account = mCore.createAccount(params);
    mCore.addAuthInfo(authInfo);
    mCore.addAccount(account);
    mCore.setDefaultAccount(account);

    mCore.start();

    mLoginCallbackContext = callbackContext;
  }

  @Override
  public void onGlobalStateChanged(Core core, GlobalState globalState, String s) {
    android.util.Log.d(TAG, "Global state changed");
    android.util.Log.d(TAG, globalState.name());
    android.util.Log.d(TAG, s);
  }

  public PluginResult callbackCustom(String message) {
    PluginResult result = new PluginResult(PluginResult.Status.OK, message);
    result.setKeepCallback(true);
    return result;
  }

  @Override
  public void onAccountRegistrationStateChanged(Core core, Account account, RegistrationState state, String message) {
    if (state == RegistrationState.Ok) {
      mLoginCallbackContext.sendPluginResult(callbackCustom("Ok"));
    } else if (state == RegistrationState.Failed) {
      mLoginCallbackContext.sendPluginResult(callbackCustom("ServiceUnavailable"));
    } else if (state == RegistrationState.Progress) {
      mLoginCallbackContext.sendPluginResult(callbackCustom("Trying"));
    }
  }

  @Override
  public void onCallStateChanged(Core core, Call call, State state, String s) {
    if (state == State.Connected) {
      toggleEnableSpeaker(true);
    } else if (state == State.End) {
      toggleEnableSpeaker(false);
    } else if (state == State.Error) {
      toggleEnableSpeaker(false);
    }

    mCallbackContext.sendPluginResult(callbackCustom(state.toString()));
    Log.d("Call state: " + state + "(" + s + ")");
  }
}
