package com.just.agentweb;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import static com.just.agentweb.ActionActivity.KEY_FROM_INTENTION;

/**
 * Created by cenxiaozhong .
 * source code  https://github.com/Justson/AgentWeb
 */
public class DefaultChromeClient extends MiddleWareWebChromeBase implements FileUploadPop<IFileUploadChooser> {


    private WeakReference<Activity> mActivityWeakReference = null;
    private AlertDialog promptDialog = null;
    private AlertDialog confirmDialog = null;
    private JsPromptResult pJsResult = null;
    private JsResult cJsResult = null;
    private String TAG = DefaultChromeClient.class.getSimpleName();
    private ChromeClientCallbackManager mChromeClientCallbackManager;
    public static final String ChromePath = "android.webkit.WebChromeClient";
    private WebChromeClient mWebChromeClient;
    private boolean isWrapper = false;
    private IFileUploadChooser mIFileUploadChooser;
    private IVideo mIVideo;
    private DefaultMsgConfig.ChromeClientMsgCfg mChromeClientMsgCfg;
    private PermissionInterceptor mPermissionInterceptor;
    private WebView mWebView;
    private String origin = null;
    private GeolocationPermissions.Callback mCallback = null;
    public static final int FROM_CODE_INTENTION = 0x18;
    public static final int FROM_CODE_INTENTION_LOCATION = FROM_CODE_INTENTION << 2;
    private WeakReference<AgentWebUIController> mAgentWebUiController = null;
    private IndicatorController mIndicatorController;

    DefaultChromeClient(Activity activity,
                        IndicatorController indicatorController,
                        WebChromeClient chromeClient,
                        ChromeClientCallbackManager chromeClientCallbackManager,
                        @Nullable IVideo iVideo,
                        DefaultMsgConfig.ChromeClientMsgCfg chromeClientMsgCfg, PermissionInterceptor permissionInterceptor, WebView webView) {
        super(chromeClient);
        this.mIndicatorController=indicatorController;
        isWrapper = chromeClient != null ? true : false;
        this.mWebChromeClient = chromeClient;
        mActivityWeakReference = new WeakReference<Activity>(activity);
        this.mChromeClientCallbackManager = chromeClientCallbackManager;
        this.mIVideo = iVideo;
        this.mChromeClientMsgCfg = chromeClientMsgCfg;
        this.mPermissionInterceptor = permissionInterceptor;
        this.mWebView = webView;
        mAgentWebUiController = new WeakReference<AgentWebUIController>(AgentWebUtils.getAgentWebUIControllerByWebView(webView));
        LogUtils.i(TAG, "controller:" + mAgentWebUiController.get());
    }


    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);

        if (mIndicatorController != null)
            mIndicatorController.progress(view, newProgress);

        ChromeClientCallbackManager.AgentWebCompatInterface mAgentWebCompatInterface = null;
        if (AgentWebConfig.WEBVIEW_TYPE == AgentWebConfig.WEBVIEW_AGENTWEB_SAFE_TYPE && mChromeClientCallbackManager != null && (mAgentWebCompatInterface = mChromeClientCallbackManager.getAgentWebCompatInterface()) != null) {
            mAgentWebCompatInterface.onProgressChanged(view, newProgress);
        }

    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        ChromeClientCallbackManager.ReceivedTitleCallback mCallback = null;
        if (mChromeClientCallbackManager != null && (mCallback = mChromeClientCallbackManager.getReceivedTitleCallback()) != null)
            mCallback.onReceivedTitle(view, title);

        if (AgentWebConfig.WEBVIEW_TYPE == AgentWebConfig.WEBVIEW_AGENTWEB_SAFE_TYPE && mChromeClientCallbackManager != null && (mChromeClientCallbackManager.getAgentWebCompatInterface()) != null)
            mChromeClientCallbackManager.getAgentWebCompatInterface().onReceivedTitle(view, title);
        if (isWrapper)
            super.onReceivedTitle(view, title);
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {


        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onJsAlert", "public boolean " + ChromePath + ".onJsAlert", WebView.class, String.class, String.class, JsResult.class)) {
            return super.onJsAlert(view, url, message, result);
        }

        if (mAgentWebUiController.get() != null) {
            mAgentWebUiController.get().onJsAlert(view, url, message);
        }

        result.confirm();

        return true;
    }


    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        super.onGeolocationPermissionsHidePrompt();
        LogUtils.i(TAG, "onGeolocationPermissionsHidePrompt");
    }

    //location
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {

        LogUtils.i(TAG, "onGeolocationPermissionsShowPrompt:" + origin + "   callback:" + callback);
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onGeolocationPermissionsShowPrompt", "public void " + ChromePath + ".onGeolocationPermissionsShowPrompt", String.class, GeolocationPermissions.Callback.class)) {
            super.onGeolocationPermissionsShowPrompt(origin, callback);
            return;
        }
        onGeolocationPermissionsShowPromptInternal(origin, callback);
    }


    private void onGeolocationPermissionsShowPromptInternal(String origin, GeolocationPermissions.Callback callback) {

        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(this.mWebView.getUrl(), AgentWebPermissions.LOCATION, "location")) {
                callback.invoke(origin, false, false);
                return;
            }
        }

        Activity mActivity = mActivityWeakReference.get();
        if (mActivity == null) {
            callback.invoke(origin, false, false);
            return;
        }

        List<String> deniedPermissions = null;
        if ((deniedPermissions = AgentWebUtils.getDeniedPermissions(mActivity, AgentWebPermissions.LOCATION)).isEmpty()) {
            LogUtils.i(TAG,"onGeolocationPermissionsShowPromptInternal:"+true);
            callback.invoke(origin, true, false);
        } else {

            ActionActivity.Action mAction = ActionActivity.Action.createPermissionsAction(deniedPermissions.toArray(new String[]{}));
            mAction.setFromIntention(FROM_CODE_INTENTION_LOCATION);
            ActionActivity.setPermissionListener(mPermissionListener);
            this.mCallback = callback;
            this.origin = origin;
            ActionActivity.start(mActivity, mAction);
        }


    }

    private ActionActivity.PermissionListener mPermissionListener = new ActionActivity.PermissionListener() {
        @Override
        public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {


            if (extras.getInt(KEY_FROM_INTENTION) == FROM_CODE_INTENTION_LOCATION) {
                boolean t = AgentWebUtils.hasPermission(mActivityWeakReference.get(), permissions);

                if (mCallback != null) {
                    if (t) {
                        mCallback.invoke(origin, true, false);
                    } else {
                        mCallback.invoke(origin, false, false);
                    }

                    mCallback = null;
                    origin = null;
                }

            }

        }
    };

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {


        try {
            if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onJsPrompt", "public boolean " + ChromePath + ".onJsPrompt", WebView.class, String.class, String.class, String.class, JsPromptResult.class)) {

                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
            if (AgentWebConfig.WEBVIEW_TYPE == AgentWebConfig.WEBVIEW_AGENTWEB_SAFE_TYPE && mChromeClientCallbackManager != null && mChromeClientCallbackManager.getAgentWebCompatInterface() != null) {

                LogUtils.i(TAG, "mChromeClientCallbackManager.getAgentWebCompatInterface():" + mChromeClientCallbackManager.getAgentWebCompatInterface());
                if (mChromeClientCallbackManager.getAgentWebCompatInterface().onJsPrompt(view, url, message, defaultValue, result))
                    return true;
            }

            if (this.mAgentWebUiController.get() != null) {
                this.mAgentWebUiController.get().onJsPrompt(mWebView, url, message, defaultValue, result);
            }
//            showJsPrompt(message, result, defaultValue);
        } catch (Exception e) {
//            e.printStackTrace();
            if (LogUtils.isDebug()) {
                e.printStackTrace();
            }
        }

        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {

        LogUtils.i(TAG,"onJsConfirm:"+ message);
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onJsConfirm", "public boolean " + ChromePath + ".onJsConfirm", WebView.class, String.class, String.class, JsResult.class)) {

            return super.onJsConfirm(view, url, message, result);
        }


        LogUtils.i(TAG, "mAgentWebUiController:" + mAgentWebUiController.get());
        if (mAgentWebUiController.get() != null) {
            mAgentWebUiController.get().onJsConfirm(view, url, message, result);
        }
//        showJsConfirm(message, result);
        return true;
    }


    private void toDismissDialog(Dialog dialog) {
        if (dialog != null)
            dialog.dismiss();

    }


    @Deprecated
    private void showJsConfirm(String message, final JsResult result) {

        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            result.cancel();
            return;
        }

        if (confirmDialog == null) {

            confirmDialog = new AlertDialog.Builder(mActivity)//
                    .setMessage(message)//
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toDismissDialog(confirmDialog);
                            toCancelJsresult(cJsResult);
                        }
                    })//
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toDismissDialog(confirmDialog);
                            if (DefaultChromeClient.this.cJsResult != null)
                                DefaultChromeClient.this.cJsResult.confirm();

                        }
                    }).create();
        }
        confirmDialog.setMessage(message);
        this.cJsResult = result;
        confirmDialog.show();

    }

    private void toCancelJsresult(JsResult result) {
        if (result != null)
            result.cancel();
    }

    @Deprecated
    private void showJsPrompt(String message, final JsPromptResult js, String defaultstr) {

        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            js.cancel();
            return;
        }
        if (promptDialog == null) {

            final EditText et = new EditText(mActivity);
            et.setText(defaultstr);
            promptDialog = new AlertDialog.Builder(mActivity)//
                    .setView(et)//
                    .setTitle(message)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toDismissDialog(promptDialog);
                            toCancelJsresult(pJsResult);
                        }
                    })//
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            toDismissDialog(promptDialog);

                            if (DefaultChromeClient.this.pJsResult != null)
                                DefaultChromeClient.this.pJsResult.confirm(et.getText().toString());

                        }
                    }).create();
        }
        this.pJsResult = js;
        promptDialog.show();

    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota, long estimatedDatabaseSize, long totalQuota, WebStorage.QuotaUpdater quotaUpdater) {


        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onExceededDatabaseQuota", ChromePath + ".onExceededDatabaseQuota", String.class, String.class, long.class, long.class, long.class, WebStorage.QuotaUpdater.class)) {

            super.onExceededDatabaseQuota(url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater);
            return;
        }
        quotaUpdater.updateQuota(totalQuota * 2);
    }

    @Override
    public void onReachedMaxAppCacheSize(long requiredStorage, long quota, WebStorage.QuotaUpdater quotaUpdater) {


        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onReachedMaxAppCacheSize", ChromePath + ".onReachedMaxAppCacheSize", long.class, long.class, WebStorage.QuotaUpdater.class)) {

            super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
            return;
        }
        quotaUpdater.updateQuota(requiredStorage * 2);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        LogUtils.i(TAG, "openFileChooser>=5.0");
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onShowFileChooser", ChromePath + ".onShowFileChooser", WebView.class, ValueCallback.class, FileChooserParams.class)) {

            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
        }

        return openFileChooserAboveL(webView, filePathCallback, fileChooserParams);
    }

    private boolean openFileChooserAboveL(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {


        LogUtils.i(TAG,"fileChooserParams:"+fileChooserParams.getAcceptTypes()+"  getTitle:"+fileChooserParams.getTitle()+" accept:"+ Arrays.toString(fileChooserParams.getAcceptTypes())+"  :"+fileChooserParams.isCaptureEnabled()+"  "+fileChooserParams.getFilenameHint()+"  intent:"+fileChooserParams.createIntent().toString());

        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            filePathCallback.onReceiveValue(new Uri[]{});
            return false;
        }
        IFileUploadChooser mIFileUploadChooser = this.mIFileUploadChooser;
        this.mIFileUploadChooser = mIFileUploadChooser = new FileUpLoadChooserImpl.Builder()
                .setWebView(webView)
                .setActivity(mActivity)
                .setUriValueCallbacks(filePathCallback)
                .setFileChooserParams(fileChooserParams)
                .setFileUploadMsgConfig(mChromeClientMsgCfg.getFileUploadMsgConfig())
                .setPermissionInterceptor(this.mPermissionInterceptor)
                .build();
        mIFileUploadChooser.openFileChooser();
        return true;

    }

    // Android  >= 4.1
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        /*believe me , i never want to do this */
        LogUtils.i(TAG, "openFileChooser>=4.1");
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "openFileChooser", ChromePath + ".openFileChooser", ValueCallback.class, String.class, String.class)) {
            super.openFileChooser(uploadFile, acceptType, capture);
            return;
        }
        createAndOpenCommonFileLoader(uploadFile,acceptType);
    }

    //  Android < 3.0
    public void openFileChooser(ValueCallback<Uri> valueCallback) {
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "openFileChooser", ChromePath + ".openFileChooser", ValueCallback.class)) {
            super.openFileChooser(valueCallback);
            return;
        }
        Log.i(TAG, "openFileChooser<3.0");
        createAndOpenCommonFileLoader(valueCallback,"*/*");
    }

    //  Android  >= 3.0
    public void openFileChooser(ValueCallback valueCallback, String acceptType) {
        Log.i(TAG, "openFileChooser>3.0");

        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "openFileChooser", ChromePath + ".openFileChooser", ValueCallback.class, String.class)) {
            super.openFileChooser(valueCallback, acceptType);
            return;
        }
        createAndOpenCommonFileLoader(valueCallback,"*/*");
    }


    private void createAndOpenCommonFileLoader(ValueCallback valueCallback,String mimeType) {
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            valueCallback.onReceiveValue(new Object());
            return;
        }
        this.mIFileUploadChooser = new FileUpLoadChooserImpl.Builder()
                .setWebView(this.mWebView)
                .setActivity(mActivity)
                .setUriValueCallback(valueCallback)
                .setFileUploadMsgConfig(mChromeClientMsgCfg.getFileUploadMsgConfig())
                .setPermissionInterceptor(this.mPermissionInterceptor)
                .setMimeType(mimeType)
                .build();
        this.mIFileUploadChooser.openFileChooser();

    }

    @Override
    public IFileUploadChooser pop() {
        Log.i(TAG, "offer:" + mIFileUploadChooser);
        IFileUploadChooser mIFileUploadChooser = this.mIFileUploadChooser;
        this.mIFileUploadChooser = null;
        return mIFileUploadChooser;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        super.onConsoleMessage(consoleMessage);
        LogUtils.i(TAG, "consoleMessage:" + consoleMessage.message() + "  lineNumber:" + consoleMessage.lineNumber());
        return true;
    }


    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        LogUtils.i(TAG, "view:" + view + "   callback:" + callback);
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onShowCustomView", ChromePath + ".onShowCustomView", View.class, CustomViewCallback.class)) {
            super.onShowCustomView(view, callback);
            return;
        }


        if (mIVideo != null)
            mIVideo.onShowCustomView(view, callback);


    }

    @Override
    public void onHideCustomView() {
        if (AgentWebUtils.isOverriedMethod(mWebChromeClient, "onHideCustomView", ChromePath + ".onHideCustomView")) {
            LogUtils.i(TAG, "onHideCustomView:" + true);
            super.onHideCustomView();
            return;
        }

        if (mIVideo != null)
            mIVideo.onHideCustomView();

    }


}
