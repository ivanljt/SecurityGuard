package com.android.rdc.mobilesafe.ui;

import android.content.SharedPreferences;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.android.rdc.mobilesafe.HomeActivity;
import com.android.rdc.mobilesafe.R;
import com.android.rdc.mobilesafe.base.BaseScrollTbActivity;
import com.android.rdc.mobilesafe.constant.Constant;

import butterknife.BindView;
import butterknife.OnClick;

public class OperatorSettingActivity extends BaseScrollTbActivity {

    @BindView(R.id.spinner_operator_select)
    Spinner mSpinnerOperatorSelect;
    @BindView(R.id.btn_finish)
    Button mBtnFinish;

    private static final String[] OPERATORS = {"中国联通", "中国电信", "中国移动"};
    private SharedPreferences mSp;


    @Override
    protected int setResId() {
        return R.layout.activity_operator_setting;
    }

    @Override
    protected void initData() {
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_expandable_list_item_1, OPERATORS);
        mSpinnerOperatorSelect.setAdapter(arrayAdapter);
        mSp = getSharedPreferences(Constant.CONFIG, MODE_PRIVATE);
    }

    @Override
    protected void initView() {

    }

    @Override
    protected void initListener() {
        mSpinnerOperatorSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSp.edit()
                        .putInt(Constant.KEY_OPERATOR, position + 1)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mSp.edit()
                        .putBoolean(Constant.HAS_SET_OPERATOR, false)
                        .apply();
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.btn_finish)
    public void onViewClicked() {
        mSp.edit()
                .putBoolean(Constant.HAS_SET_OPERATOR, true)
                .apply();
        startActivity(TrafficMonitoringActivity.class);
    }

    @Override
    public void showPre() {
        startActivityAndFinishSelf(HomeActivity.class);
    }

    @Override
    public void showNext() {
        startActivityAndFinishSelf(EnterPwdActivity.class);
    }
}