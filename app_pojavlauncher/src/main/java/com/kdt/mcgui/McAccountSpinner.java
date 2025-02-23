package com.kdt.mcgui;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.microsoft.PresentedException;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.spse.extended_view.ExtendedTextView;

public class McAccountSpinner extends AppCompatSpinner implements AdapterView.OnItemSelectedListener {
    public McAccountSpinner(@NonNull Context context) {
        this(context, null);
    }
    public McAccountSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private final List<String> mAccountList = new ArrayList<>(2);
    private static MinecraftAccount mSelectecAccount = null;

    /* Display the head of the current profile, here just to allow bitmap recycling */
    private BitmapDrawable mHeadDrawable;

    /* Current animator to for the login bar, is swapped when changing step */
    private ObjectAnimator mLoginBarAnimator;
    private float mLoginBarWidth = -1;

    /* Paint used to display the bottom bar, to show the login progress. */
    private final Paint mLoginBarPaint = new Paint();

    /* When a login is performed in the background, we need to know where we are */
    private final static int MAX_LOGIN_STEP = 5;
    private int mLoginStep = 0;
    private ImageView userIcon;
    private AccountAdapter accountAdapter;

    /* Login listeners */
    private final ProgressListener mProgressListener = step -> {
        // Animate the login bar, cosmetic purposes only
        mLoginStep = step;
        if(mLoginBarAnimator != null){
            mLoginBarAnimator.cancel();
            mLoginBarAnimator.setFloatValues( mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }else{
            mLoginBarAnimator = ObjectAnimator.ofFloat(this, "LoginBarWidth", mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }
        mLoginBarAnimator.start();
    };

    private final DoneListener mDoneListener = account -> {
        Toast.makeText(getContext(), R.string.main_login_done, Toast.LENGTH_SHORT).show();

        // Check if the account being added is not one that is already existing
        // Like login twice on the same mc account...
        for(String mcAccountName : mAccountList){
            if(mcAccountName.equals(account.username)) return;
        }

        mSelectecAccount = account;
        invalidate();
        mAccountList.add(account.username);
        reloadAccounts(false, mAccountList.size() -1);
    };

    private final ErrorListener mErrorListener = errorMessage -> {
        mLoginBarPaint.setColor(Color.RED);
        Context context = getContext();
        if(errorMessage instanceof PresentedException) {
            PresentedException exception = (PresentedException) errorMessage;
            Throwable cause = exception.getCause();
            if(cause == null) {
                Tools.dialog(context, context.getString(R.string.global_error), exception.toString(context));
            }else {
                Tools.showError(context, exception.toString(context), exception.getCause());
            }
        }else {
            Tools.showError(getContext(), errorMessage);
        }
        invalidate();
    };

    /* Triggered when we need to do microsoft login */
    private final ExtraListener<Uri> mMicrosoftLoginListener = (key, value) -> {
        mLoginBarPaint.setColor(getResources().getColor(R.color.theme_color_secondary));
        new MicrosoftBackgroundLogin(false, value.getQueryParameter("code")).performLogin(
                mProgressListener, mDoneListener, mErrorListener);
        return false;
    };

    /* Triggered when we need to perform mojang login */
    private final ExtraListener<String[]> mMojangLoginListener = (key, value) -> {
        if(value[1].isEmpty()){ // Test mode
            MinecraftAccount account = new MinecraftAccount();
            account.username = value[0];
            try {
                account.save();
            }catch (IOException e){
                Log.e("McAccountSpinner", "Failed to save the account : " + e);
            }

            mDoneListener.onLoginDone(account);
        }
        return false;
    };


    @SuppressLint("ClickableViewAccessibility")
    private void init(){
        // Set visual properties
        mLoginBarPaint.setColor(getResources().getColor(R.color.theme_color_primary));
        mLoginBarPaint.setStrokeWidth(getResources().getDimensionPixelOffset(R.dimen._2sdp));

        // Set behavior
        reloadAccounts(true, 0);
        setOnItemSelectedListener(this);

        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, mMojangLoginListener);
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, mMicrosoftLoginListener);
    }


    @Override
    public final void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        pickAccount(position);
        if (mSelectecAccount != null) {
            performLogin(mSelectecAccount);
            refreshUserIcon();
        }
    }

    @Override
    public final void onNothingSelected(AdapterView<?> parent) {}


    @Override
    protected void onDraw(Canvas canvas) {
        if(mLoginBarWidth == -1) mLoginBarWidth = getWidth(); // Initial draw

        float bottom = getHeight() - mLoginBarPaint.getStrokeWidth()/2;
        canvas.drawLine(0, bottom, mLoginBarWidth, bottom, mLoginBarPaint);
    }

    public void removeCurrentAccount(){
        int position = getSelectedItemPosition();
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, mAccountList.get(position)+".json");
        if(accountFile.exists()) accountFile.delete();
        mAccountList.remove(position);
        reloadAccounts(false, 0);
    }

    @Keep
    public void setLoginBarWidth(float value){
        mLoginBarWidth = value;
        invalidate(); // Need to redraw each time this is changed
    }

    /** Allows checking whether we have an online account */
    public boolean isAccountOnline(){
        return mSelectecAccount != null && !mSelectecAccount.accessToken.equals("0");
    }

    public static MinecraftAccount getSelectedAccount(){
        return mSelectecAccount;
    }

    public int getLoginState(){
        return mLoginStep;
    }

    public boolean isLoginDone(){
        return mLoginStep >= MAX_LOGIN_STEP;
    }

    /**
     * Reload the spinner, from memory or from scratch. A default account can be selected
     * @param fromFiles Whether we use files as the source of truth
     * @param overridePosition Force the spinner to be at this position, if not 0
     */
    private void reloadAccounts(boolean fromFiles, int overridePosition){
        if(fromFiles){
            mAccountList.clear();

            File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
            if(accountFolder.exists()){
                for (String fileName : accountFolder.list()) {
                    mAccountList.add(fileName.substring(0, fileName.length() - 5));
                }
            }
            if (!mAccountList.isEmpty()) {
                String name = PojavProfile.getCurrentProfileName(getContext());
                if (mAccountList.contains(name)) {
                    overridePosition = mAccountList.indexOf(name);
                }
            }
        }

        if (mAccountList.isEmpty()) {
            mAccountList.add(getContext().getString(R.string.main_add_account));
        } else if (mAccountList.contains(getContext().getString(R.string.main_add_account))) {
            mAccountList.remove(getContext().getString(R.string.main_add_account));
            overridePosition--;
        }
        if (accountAdapter == null) {
            accountAdapter = new AccountAdapter(getContext(), R.layout.item_minecraft_account, mAccountList);
            accountAdapter.setDropDownViewResource(R.layout.item_minecraft_account);
            setAdapter(accountAdapter);
        } else {
            accountAdapter.notifyDataSetChanged();
        }

        // Pick what's available, might just be the the add account "button"
        pickAccount(overridePosition);
        if (mSelectecAccount != null) {
            performLogin(mSelectecAccount);
            refreshUserIcon();
        }
    }

    private void performLogin(MinecraftAccount minecraftAccount){
        if(minecraftAccount.isLocal()) return;

        mLoginBarPaint.setColor(getResources().getColor(R.color.theme_color_primary));
        if(minecraftAccount.isMicrosoft){
            if(System.currentTimeMillis() > minecraftAccount.expiresAt){
                // Perform login only if needed
                new MicrosoftBackgroundLogin(true, minecraftAccount.msaRefreshToken)
                        .performLogin(mProgressListener, mDoneListener, mErrorListener);
            }
            return;
        }
    }

    /** Pick the selected account, the one in settings if 0 is passed */
    private void pickAccount(int position){
        MinecraftAccount selectedAccount = null;
        String name = mAccountList.get(position);
        if (!name.equals(getContext().getString(R.string.main_add_account))) {
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), name);
            PojavProfile.setCurrentProfile(getContext(), name);
            if (selectedAccount == null) {
                removeCurrentAccount();
                return;
            }
            setSelection(position);
        }
        mSelectecAccount = selectedAccount;
    }

    public void refreshUserIcon() {
        if (mSelectecAccount == null || userIcon == null) {
            return;
        }
        if (mSelectecAccount.isMicrosoft) {
            File file = MinecraftAccount.getSkinFaceFile(mSelectecAccount.username);
            if (file.exists()) {
                userIcon.setImageDrawable(new BitmapDrawable(
                        getResources(),
                        mSelectecAccount.getSkinFace()
                ));
            } else {
                PojavApplication.sExecutorService.execute(() -> {
                            mSelectecAccount.updateSkinFace();
                            McAccountSpinner.this.post(() -> {
                                userIcon.setImageDrawable(
                                        new BitmapDrawable(
                                                getResources(),
                                                mSelectecAccount.getSkinFace()
                                        )
                                );
                            });
                        }
                );
            }
        } else {
            userIcon.setImageResource(R.drawable.ic_steve);
        }
    }

    public void setUserIcon(ImageView userIcon) {
        this.userIcon = userIcon;
    }

    private static class AccountAdapter extends ArrayAdapter<String> {
        public AccountAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getView(position, convertView, parent);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if(convertView == null){
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_minecraft_account, parent, false);
            }
            ExtendedTextView textview = (ExtendedTextView) convertView;
            textview.setText(super.getItem(position));
            return convertView;
        }
    }

}
