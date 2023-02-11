package ks.apps.train;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

import ks.apps.train.controllers.Ads;
import ks.apps.train.controllers.Config;
import ks.apps.train.presenter.WebViewPresenterImpl;
import ks.apps.train.util.AdBlocker;
import ks.apps.train.util.ClipboardUtils;
import ks.apps.train.util.FileUtils;
import ks.apps.train.util.PermissionUtils;

public class WebViewPortrait extends AppCompatActivity implements WebViewPresenterImpl.View,
        View.OnCreateContextMenuListener, DownloadListener {

    private View decorView;
    Ads ads = new Ads();
    private TextView counter;
    private WebView mWebView;
    private LinearLayout root;
//    private FrameLayout frameLayout;
//    boolean showed = false;
//    private IronSourceBannerLayout bannerLayoutIS_;
    private boolean working = true;

    private static final String TAG = "AdBlocksWebViewActivity";
    private static final int REQUEST_FILE_CHOOSER = 0;
    private static final int REQUEST_FILE_CHOOSER_FOR_LOLLIPOP = 1;
    private static final int REQUEST_PERMISSION_SETTING = 2;

    public static final String EXTRA_URL = "url";

    private String mUrl;
    private ValueCallback<Uri[]> filePathCallbackLollipop;
    private ValueCallback<Uri> filePathCallbackNormal;

    private WebViewPresenterImpl mPresenter;
    private DownloadManager mDownloadManager;
    private long mLastDownloadId;

    private CoordinatorLayout mCoordinatorLayout;

    public static void init(Context context) {
        AdBlocker.init(context);
    }

    public static void startWebView(Activity context, @NonNull final String URL) {
        Intent intent = new Intent(context, WebViewPortrait.class);
        intent.putExtra(WebViewPortrait.EXTRA_URL, URL);
        context.startActivity(intent);
        context.overridePendingTransition(R.anim.open_translate, R.anim.close_scale);
    }

    public static void startWebViewForResult(Activity context, @NonNull final String URL, @ColorInt final int toolbarColor, int requestCode) {
        Intent intent = new Intent(context, WebViewPortrait.class);
        intent.putExtra(WebViewPortrait.EXTRA_URL, URL);
        context.startActivityForResult(intent, requestCode);
        context.overridePendingTransition(R.anim.open_translate, R.anim.close_scale);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //getWindow().setStatusBarColor(getIntent().getIntExtra(EXTRA_COLOR, Color.BLACK));
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
        registerReceiver(mDownloadReceiver, filter);

        mUrl = getIntent().getStringExtra(EXTRA_URL);

        setContentView(R.layout.activity_play);

        decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility == 0)
                    decorView.setSystemUiVisibility(hideSystemBars());
            }
        });

        ids();
        actions();

        mPresenter = new WebViewPresenterImpl(this, this);
        mPresenter.validateUrl(mUrl);
    }

    private void ids() {
//        frameLayout = findViewById(R.id.ad_frame_banner);
        counter = findViewById(R.id.counter);
        root = findViewById(R.id.root);
        mWebView = (WebView) findViewById(R.id.webview);
        mCoordinatorLayout = findViewById(R.id.a_web_viewer_coordinator_layout);
    }

    private void actions() {
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setSupportZoom(true);
        webSettings.setDomStorageEnabled(true);

        mWebView.setWebChromeClient(new MyWebChromeClient());
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.setDownloadListener(this);
        mWebView.setOnCreateContextMenuListener(this);

        findViewById(R.id.close).setOnClickListener(v -> {
            Animation hang_fall = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.zoom_in);
            hang_fall.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationEnd(Animation animation) {
                    Config.startAdProgress(WebViewPortrait.this);
                }
                public void onAnimationRepeat(Animation animation) {}
                public void onAnimationStart(Animation animation) {
                }
            });
            v.startAnimation(hang_fall);
        });

        if (SplashActivity.activate_inter.equals("on")) {
            countdown();
        }else if (SplashActivity.activate_banner.equals("on")){
            countdown();
        }

    }

    private void countdown() {
        int seconds = SplashActivity.ad_game_minutes * 60 * 1000;
        new CountDownTimer(seconds, 1000) {
            public void onTick(long millisUntilFinished) {
                counter.setText("" + millisUntilFinished / 1000);
                if (millisUntilFinished / 1000 < 11){
                    counter.setVisibility(View.VISIBLE);
                }
//                if(!showed){
//                    if (millisUntilFinished / 1000 < 21){
//                        if (SplashActivity.activate_banner.equals("on")) {
//                            frameLayout.setVisibility(View.VISIBLE);
//                            showed = true;
//                            if (SplashActivity.network.equals("is")) {
//                                if (bannerLayoutIS_!=null){
//                                    IronSource.destroyBanner(bannerLayoutIS_);
//                                }
//                                isBanner_(WebViewPortrait.this, frameLayout);
//                            }else{
//                                new Ads().banner(WebViewPortrait.this, frameLayout);
//                            }
//                        }else{
//                            frameLayout.setVisibility(View.GONE);
//                        }
//                    }
//                }
            }
            public void onFinish() {
//                if (bannerLayoutIS_!=null){
//                    IronSource.destroyBanner(bannerLayoutIS_);
//                }
//                frameLayout.setVisibility(View.GONE);
//                showed = false;
                counter.setVisibility(View.GONE);
                if (working) {
                    if (SplashActivity.activate_inter.equals("on"))
                        ads.interstitial(WebViewPortrait.this, false, () -> countdown());
                    else
                        countdown();
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mDownloadReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        WebView.HitTestResult result = mWebView.getHitTestResult();

        mPresenter.onLongClick(result);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mUrl != null) {
            outState.putString(EXTRA_URL, mUrl);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mUrl = savedInstanceState.getString(EXTRA_URL);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (RESULT_OK == resultCode) {
            switch (requestCode) {
                case REQUEST_FILE_CHOOSER: {
                    if (filePathCallbackNormal == null) {
                        return;
                    }
                    Uri result = data == null ? null : data.getData();
                    filePathCallbackNormal.onReceiveValue(result);
                    filePathCallbackNormal = null;
                    break;
                }
                case REQUEST_FILE_CHOOSER_FOR_LOLLIPOP: {
                    if (filePathCallbackLollipop == null) {
                        return;
                    }
                    filePathCallbackLollipop.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                    filePathCallbackLollipop = null;
                    break;
                }
                case REQUEST_PERMISSION_SETTING: {
                    mLastDownloadId = FileUtils.downloadFile(this, mDownloadUrl, mDownloadMimetype);
                    break;
                }
            }
        } else {
            switch (requestCode) {
                case REQUEST_PERMISSION_SETTING: {
                    Toast.makeText(this, R.string.write_permission_denied_message, Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            decorView.setSystemUiVisibility(hideSystemBars());
    }

    private int hideSystemBars(){
        return View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    }

    @Override
    public void loadUrl(String url) {
        mWebView.loadUrl(url);
        mPresenter.onReceivedTitle("", mUrl);
    }

    @Override
    public void close() {

    }

    @Override
    public void closeMenu() {

    }

    @Override
    public void openMenu() {

    }

    @Override
    public void setEnabledGoBackAndGoFoward() {

    }

    @Override
    public void setDisabledGoBackAndGoFoward() {

    }

    @Override
    public void setEnabledGoBack() {

    }

    @Override
    public void setDisabledGoBack() {

    }

    @Override
    public void setEnabledGoFoward() {

    }

    @Override
    public void setDisabledGoFoward() {

    }

    @Override
    public void goBack() {

    }

    @Override
    public void goFoward() {

    }

    @Override
    public void copyLink(String url) {
        ClipboardUtils.copyText(this, url);
    }

    @Override
    public void showToast(Toast toast) {
        toast.show();
    }

    @Override
    public void openBrowser(Uri uri) {
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    @Override
    public void openShare(String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, mWebView.getUrl());
        intent.setType("text/plain");
        startActivity(Intent.createChooser(intent, getResources().getString(R.string.menu_share)));
    }

    @Override
    public void setToolbarTitle(String title) {

    }

    @Override
    public void setToolbarUrl(String url) {

    }

    @Override
    public void onDownloadStart(String url) {
        onDownloadStart(url, null, null, "image/jpeg", 0);
    }

    @Override
    public void setProgressBar(int progress) {

    }

    @Override
    public void setRefreshing(boolean refreshing) {

    }

    @Override
    public void openEmail(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null));
        startActivity(Intent.createChooser(intent, getString(R.string.email)));
    }

    @Override
    public void openPopup(String url) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE == requestCode) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mLastDownloadId = FileUtils.downloadFile(this, mDownloadUrl, mDownloadMimetype);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!shouldShowRequestPermissionRationale(permissions[0])) {
                        new AlertDialog.Builder(WebViewPortrait.this)
                                .setTitle(R.string.write_permission_denied_title)
                                .setMessage(R.string.write_permission_denied_message)
                                .setNegativeButton(R.string.dialog_dismiss, null)
                                .setPositiveButton(R.string.dialog_settings, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                                        intent.setData(uri);
                                        startActivityForResult(intent, REQUEST_PERMISSION_SETTING);
                                    }
                                })
                                .show();
                    }
                }
            }
        }
    }

    private String mDownloadUrl;
    private String mDownloadMimetype;

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                String mimeType, long contentLength) {
        if (mDownloadManager == null) {
            mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        }
        Log.d(TAG, "onDownloadStart url: " + url);
        Log.d(TAG, "onDownloadStart userAgent: " + userAgent);
        Log.d(TAG, "onDownloadStart contentDisposition: " + contentDisposition);
        Log.d(TAG, "onDownloadStart mimeType: " + mimeType);

        mDownloadUrl = url;
        mDownloadMimetype = mimeType;

        boolean hasPermission = PermissionUtils.hasPermission(
                WebViewPortrait.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                PermissionUtils.REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);

        if (hasPermission) {
            mLastDownloadId = FileUtils.downloadFile(this, url, mimeType);
        }
    }

    @Override
    public void onRefresh() {
        mWebView.reload();
    }

    public class MyWebChromeClient extends WebChromeClient {

        // For Android < 3.0
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            openFileChooser(uploadMsg, "");
        }

        // For Android 3.0+
        @SuppressWarnings("WeakerAccess")
        public void openFileChooser(ValueCallback<Uri> uploadMsg,
                                    @SuppressWarnings("UnusedParameters") String acceptType) {
            filePathCallbackNormal = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, getString(R.string.select_image)),
                    REQUEST_FILE_CHOOSER);
        }

        // For Android 5.0+
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (filePathCallbackLollipop != null) {
                filePathCallbackLollipop.onReceiveValue(null);
                filePathCallbackLollipop = null;
            }
            filePathCallbackLollipop = filePathCallback;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                    REQUEST_FILE_CHOOSER_FOR_LOLLIPOP);

            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            AlertDialog dialog = new AlertDialog.Builder(WebViewPortrait.this)
                    .setMessage(message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            AlertDialog dialog = new AlertDialog.Builder(WebViewPortrait.this)
                    .setMessage(message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int progress) {
//            mPresenter.onProgressChanged(mSwipeRefreshLayout, progress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            mPresenter.onReceivedTitle(title, view.getUrl());
        }
    }

    public class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            mPresenter.onReceivedTitle(view.getTitle(), url);
            mPresenter.setEnabledGoBackAndGoFoward(view.canGoBack(), view.canGoForward());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.endsWith(".mp4")) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                view.getContext().startActivity(intent);

                return true;
            } else if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("smsto:")
                    || url.startsWith("mms:") || url.startsWith("mmsto:")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                view.getContext().startActivity(intent);

                return true;
            } else {
                return super.shouldOverrideUrlLoading(view, url);
            }
        }

        private Map<String, Boolean> loadedUrls = new HashMap<>();

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }
            return ad ? AdBlocker.createEmptyResource() :
                    super.shouldInterceptRequest(view, url);
        }
    }

    private BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                if (mDownloadManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        Uri downloadedUri = mDownloadManager.getUriForDownloadedFile(mLastDownloadId);
                        String mimeType = mDownloadManager.getMimeTypeForDownloadedFile(mLastDownloadId);

                        new NotifyDownloadedTask().execute(downloadedUri.toString(), mimeType);
                    }
                }
            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
                Intent notiIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                notiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(notiIntent);
            }
        }
    };

    private class NotifyDownloadedTask extends AsyncTask<String, Void, String[]> {

        @Override
        protected String[] doInBackground(String... params) {
            if (params == null || params.length != 2) {
                return null;
            }
            String uriStr = params[0];
            String mimeType = params[1];
            String fileName = "";

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(mLastDownloadId);
            Cursor c = mDownloadManager.query(query);

            if (c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (DownloadManager.STATUS_SUCCESSFUL == status) {
                    fileName = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE));
                }
            }

            return new String[]{uriStr, fileName, mimeType};
        }

        @Override
        protected void onPostExecute(String[] results) {
            if (results != null && results.length == 3) {
                final String uriStr = results[0];
                final String fileName = results[1];
                final String mimeType = results[2];

                //noinspection WrongConstant
                Snackbar.make(mCoordinatorLayout, fileName + getString(R.string.downloaded_message),
                        Snackbar.LENGTH_LONG)
                        .setDuration(getResources().getInteger(R.integer.snackbar_duration))
                        .setAction(getString(R.string.open), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                                viewIntent.setDataAndType(Uri.parse(uriStr), mimeType);
                                mPresenter.startActivity(viewIntent);
                            }
                        })
                        .show();
            }
        }
    }

    protected void overridePendingTransitionExit() {
        overridePendingTransition(R.anim.fade_out, R.anim.fade_out);
    }

    @Override
    public void finish() {
        working = false;
//        if (bannerLayoutIS_!=null){
//            IronSource.destroyBanner(bannerLayoutIS_);
//        }
        super.finish();
        overridePendingTransitionExit();
    }

//    private void isBanner_(Activity activity, FrameLayout ad_frame){
//        bannerLayoutIS_ = IronSource.createBanner(activity, ISBannerSize.BANNER);
//        bannerLayoutIS_.setBannerListener(new BannerListener() {
//            @Override
//            public void onBannerAdLoaded() {
//                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
//                        LinearLayout.LayoutParams.MATCH_PARENT,
//                        LinearLayout.LayoutParams.WRAP_CONTENT);
//                ad_frame.removeAllViews();
//                ad_frame.addView(bannerLayoutIS_, 0, layoutParams);
//            }
//            @Override
//            public void onBannerAdLoadFailed(IronSourceError error) {
//                ad_frame.setVisibility(View.GONE);
//            }
//            @Override
//            public void onBannerAdClicked() {}
//            @Override
//            public void onBannerAdScreenPresented() {}
//            @Override
//            public void onBannerAdScreenDismissed() { }
//            @Override
//            public void onBannerAdLeftApplication() {}
//        });
//        IronSource.loadBanner(bannerLayoutIS_, SplashActivity.iron_ban);
//    }

    @Override
    public void onBackPressed() {}
}
