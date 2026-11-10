package com.maico.refurbished;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Five lightweight pages: Vance, Sources, Refurbish, Signing and shared Output. */
final class UiSections {
    final LinearLayout[] panels=new LinearLayout[5];
    private final Button[] tabs=new Button[5];
    private final ScrollView scroll;
    private final ProgressBar progress;
    private final Activity activity;
    private final boolean dark;
    private int active;

    UiSections(Activity activity,ScrollView scroll,LinearLayout host) {
        this.activity=activity;this.scroll=scroll;
        this.dark=UiSettings.dark(activity);
        LinearLayout navigation=new LinearLayout(activity);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(5),dp(5),dp(5),dp(5));
        navigation.setBackground(outline(dark?0xff1d1d1d:0xfffafafa,
                dark?0xff777777:0xffc7c7c7,12));
        String[] captions={
                activity.getString(R.string.tab_home),
                activity.getString(R.string.tab_sources),
                activity.getString(R.string.tab_styles),
                activity.getString(R.string.tab_sign),
                activity.getString(R.string.tab_output)
        };
        for(int i=0;i<5;i++){
            final int index=i;
            Button tab=new Button(activity);
            tab.setAllCaps(false);
            tab.setText(captions[i]);
            tab.setTextSize(12);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(56));
            tab.setMinimumHeight(dp(56));
            tab.setPadding(dp(2),dp(4),dp(2),dp(4));
            int[] iconIds={R.drawable.ic_vance_home,R.drawable.ic_flat_source,R.drawable.ic_vance_refurbish,
                    R.drawable.ic_flat_key,R.drawable.ic_flat_package};
            FlatUi.tab(activity,tab,iconIds[i]);
            tab.setOnClickListener(v->show(index));
            tabs[i]=tab;
            LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(
                    0,dp(56),1);
            tabParams.setMargins(dp(2),0,dp(2),0);
            navigation.addView(tab,tabParams);
        }
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        navParams.topMargin=dp(12);
        navParams.bottomMargin=dp(6);
        host.addView(navigation,navParams);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        host.addView(progress);
        for(int i=0;i<5;i++){
            LinearLayout section=new LinearLayout(activity);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setPadding(0,8,0,16);
            section.setVisibility(i==0?View.VISIBLE:View.GONE);
            panels[i]=section;
            host.addView(section);
        }
        show(0);
    }
    void show(int index) {
        if(index<0 || index>=5)return;
        active=index;
        for(int i=0;i<5;i++){
            panels[i].setVisibility(i==index?View.VISIBLE:View.GONE);
            boolean chosen=i==index;
            tabs[i].setAlpha(1f);
            tabs[i].setTypeface(null,chosen?Typeface.BOLD:Typeface.NORMAL);
            int fg=chosen?(dark?Color.BLACK:Color.WHITE):
                    (dark?Color.WHITE:Color.BLACK);
            tabs[i].setTextColor(fg);
            int[] iconIds={R.drawable.ic_vance_home,R.drawable.ic_flat_source,R.drawable.ic_vance_refurbish,
                    R.drawable.ic_flat_key,R.drawable.ic_flat_package};
            FlatUi.icon(activity,tabs[i],iconIds[i],fg,true);
            tabs[i].setBackground(outline(
                    chosen?(dark?Color.WHITE:0xff171717):
                            (dark?0xff1d1d1d:Color.WHITE),
                    chosen?(dark?Color.WHITE:0xff171717):
                            (dark?0xff777777:0xffbbbbbb),9));
        }
        scroll.post(()->scroll.smoothScrollTo(0,0));
    }
    void busy(boolean state){progress.setVisibility(state?View.VISIBLE:View.GONE);}
    int current(){return active;}
    private int dp(int value){
        return (int)(value*activity.getResources().getDisplayMetrics().density+0.5f);
    }
    private GradientDrawable outline(int fill,int stroke,int radius){
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1),stroke);
        return drawable;
    }
}
