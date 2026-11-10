package com.maico.refurbished;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;

/** Monochrome vector + outlined XML button styling for both interface themes. */
final class FlatUi {
    private FlatUi(){}
    private static int dp(Activity a,int n){
        return (int)(n*a.getResources().getDisplayMetrics().density+0.5f);
    }
    static void button(Activity a,Button button,int drawableId){
        boolean dark=UiSettings.dark(a);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(a,52));
        button.setMinimumHeight(dp(a,52));
        button.setPadding(dp(a,14),dp(a,10),dp(a,12),dp(a,10));
        button.setBackgroundResource(dark?R.drawable.flat_button_dark:R.drawable.flat_button_light);
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{dark?0xff888888:0xff999999,dark?Color.WHITE:0xff191919}));
        icon(a,button,drawableId,dark?Color.WHITE:0xff171717,false);
    }
    static void tab(Activity a,Button button,int drawableId){
        icon(a,button,drawableId,UiSettings.dark(a)?Color.WHITE:Color.BLACK,true);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablePadding(dp(a,2));
    }
    static void icon(Activity a,Button button,int drawableId,int tint,boolean above){
        Drawable icon=a.getDrawable(drawableId);
        if(icon==null)return;
        icon=icon.mutate();
        icon.setTint(tint);
        icon.setBounds(0,0,dp(a,above?20:22),dp(a,above?20:22));
        if(above)button.setCompoundDrawables(null,icon,null,null);
        else button.setCompoundDrawables(icon,null,null,null);
        if(!above)button.setCompoundDrawablePadding(dp(a,10));
    }
}
