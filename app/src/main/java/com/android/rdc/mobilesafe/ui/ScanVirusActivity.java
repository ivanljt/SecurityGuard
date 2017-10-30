package com.android.rdc.mobilesafe.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.rdc.mobilesafe.R;
import com.android.rdc.mobilesafe.adapter.ScanAppVirusAdapter;
import com.android.rdc.mobilesafe.base.BaseToolBarActivity;
import com.android.rdc.mobilesafe.base.BaseSafeActivityHandler;
import com.android.rdc.mobilesafe.dao.AntiVirusDao;
import com.android.rdc.mobilesafe.entity.CustomEvent;
import com.android.rdc.mobilesafe.entity.ScanAppInfo;
import com.android.rdc.mobilesafe.util.IOUtil;
import com.android.rdc.mobilesafe.util.MD5Utils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ScanVirusActivity extends BaseToolBarActivity {
    private static final String TAG = "ScanVirusActivity";
    @BindView(R.id.tv_scan_process)
    TextView mTvScanProcess;
    @BindView(R.id.iv_scanning_app_icon)
    ImageView mIvScanningAppIcon;
    @BindView(R.id.tv_scanning_app_name)
    TextView mTvScanningAppName;
    @BindView(R.id.rv)
    RecyclerView mRv;
    @BindView(R.id.btn_cancel_scanning)
    Button mBtnCancelScanning;
    @BindView(R.id.progressbar)
    ProgressBar mProgressbar;

    private ImageView mScanningIcon;

    private int mTotal;
    private int mProcess;
    private PackageManager mPackageManager;
    private boolean mIsStop;
    private boolean mFlag;
    private ScanAppVirusAdapter mAdapter;

    private ExecutorService mExecutorService;//线程池

    private List<ScanAppInfo> mScanAppInfoList = new ArrayList<>(32);

    public static final int BEGIN_SCANNING = 101;
    public static final int SCANNING = 102;
    public static final int COMPLETE_SCANNING = 103;

    private static class ProgressHandlerBaseActivity extends BaseSafeActivityHandler<ScanVirusActivity> {

        public ProgressHandlerBaseActivity(ScanVirusActivity activityReference) {
            super(activityReference);
        }

        public void handleMessage(Message msg) {
            ScanVirusActivity activity = getActivity();
            if (activity == null) return;
            switch (msg.what) {
                case BEGIN_SCANNING:
                    activity.mTvScanningAppName.setText("初始化杀毒引擎...");
                    activity.startAnim();
                    break;
                case SCANNING:
                    ScanAppInfo scanAppInfo = (ScanAppInfo) msg.obj;
                    activity.mTvScanningAppName.setText(String.format("正在扫描：%s", scanAppInfo.getAppName()));
                    int process = msg.arg1;
                    //设置进度，
                    int currentProcess = process * 100 / activity.mTotal;
                    String str = currentProcess + "%";
                    activity.mTvScanProcess.setText(str);
                    activity.mTvScanningAppName.setText(scanAppInfo.getAppName());
                    // 设置选中项最新扫描完成的项
//                    mScanAppInfoList.add(scanAppInfo);
                    activity.mAdapter.getDataList().add(scanAppInfo);
                    activity.mAdapter.notifyDataSetChanged();
                    activity.mRv.scrollToPosition(activity.mAdapter.getDataList().size() - 1);
                    break;
                case COMPLETE_SCANNING:
//                    mScanningIcon.clearAnimation();
                    activity.mBtnCancelScanning.setText("扫描完成");
                    activity.mProgressbar.setProgress(100);
                    activity.mIsStop = true;
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    private Handler mHandler = new ProgressHandlerBaseActivity(this);

    private void startAnim() {
//        Animation animation = mScanningIcon.getAnimation();

    }

    @Override
    protected int setResId() {
        return R.layout.activity_virus_scan;
    }

    @Override
    protected void initData() {
        mExecutorService = Executors.newFixedThreadPool(3);
        copyDB("antivirus.db");
        mPackageManager = getPackageManager();
        EventBus.getDefault().register(this);//注册

    }

    @Override
    protected void initView() {
        setTitle("病毒查杀");
        scanApp();
        mAdapter = new ScanAppVirusAdapter();
        mRv.setLayoutManager(new LinearLayoutManager(this));
        mRv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mRv.setAdapter(mAdapter);
    }

    private void scanApp() {
        mIsStop = false;
        mFlag = true;
        mProcess = 0;
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                //获取所有安装的应用
                List<PackageInfo> scanAppInfoList = mPackageManager.getInstalledPackages(0);
                mTotal = scanAppInfoList.size();

                Message msgStart = mHandler.obtainMessage(BEGIN_SCANNING);
                mHandler.sendMessage(msgStart);

                //遍历应用，计算应用文件的特征码，与病毒库中的数据进行比较
                for (PackageInfo packageInfo : scanAppInfoList) {
                    if (mIsStop) {
                        return;
                    }
                    String apkPath = packageInfo.applicationInfo.sourceDir;
                    String md5Info = MD5Utils.getFileMD5(apkPath);
                    String result = AntiVirusDao.checkVirus(md5Info);

                    ++mProcess;
                    ScanAppInfo scanAppInfo = new ScanAppInfo();
                    scanAppInfo.setAppName(packageInfo.applicationInfo.loadLabel(mPackageManager).toString());
                    scanAppInfo.setIcon(packageInfo.applicationInfo.loadIcon(mPackageManager));
                    scanAppInfo.setPackageName(packageInfo.packageName);

                    if (result == null) {
                        scanAppInfo.setDescription("扫描安全");
                    } else {
                        scanAppInfo.setDescription(result);
                        scanAppInfo.setVirus(true);
                    }
                    // 每次循环中，发送一个消息到主线程，状态为正在扫描，同时发送正在被扫描的应用信息 ，以及扫描的进度
                    Message message = mHandler.obtainMessage(SCANNING);
                    message.arg1 = mProcess;
                    message.obj = scanAppInfo;
                    mHandler.sendMessage(message);
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //扫描完成
                Message message = mHandler.obtainMessage(COMPLETE_SCANNING);
                mHandler.sendMessage(message);
            }
        });
    }

    @Override
    protected void initListener() {

    }

    /**
     * 将 asset 目录下的文件复制到 data 目录下
     */
    private void copyDB(final String dbName) {
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(getFilesDir(), dbName);
                if (file.exists() && file.length() > 0) {
                    Log.i(TAG, "数据库已经存在");
                    return;
                }
                InputStream inputStream = null;
                FileOutputStream outputStream = null;
                try {
                    inputStream = getAssets().open(dbName);//原存储位置的输出流
                    outputStream = openFileOutput(dbName, MODE_PRIVATE);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtil.closeQuietly(inputStream);
                    IOUtil.closeQuietly(outputStream);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);//清空消息，防止内存泄漏
        EventBus.getDefault().unregister(this);//解除注册

        super.onDestroy();
    }

    /**
     * 监听「取消扫描按钮」点击事件
     */
    @OnClick(R.id.btn_cancel_scanning)
    public void onViewClicked() {
        if (mProcess == mTotal && mProcess > 0) {//注意 process 要 > 0,该比较才有意义
            finish();
        } else {

        }
        //扫描已完成，点击后回退
        //扫描未完成，点击后停止扫描
        //
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true, priority = 1000)
    public void onMessage(CustomEvent event) {
        Log.d(TAG, "onMessage: " + event.getEventName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: add setContentView(...) invocation
        ButterKnife.bind(this);
    }
}