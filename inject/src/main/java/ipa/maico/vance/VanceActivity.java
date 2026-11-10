package ipa.maico.vance;
import ipa.maico.furbish.ChangeDisplayActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Static-shortcut entry point. All Vance UI runs in the dedicated :vance process. */
public final class VanceActivity extends Activity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  ScrollView scroll=new ScrollView(this);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("VANCE");title.setTextSize(25);
  title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.BLACK);
  title.setGravity(Gravity.CENTER);title.setPadding(0,dp(4),0,dp(4));root.addView(title);
  TextView sub=new TextView(this);sub.setText("Control center");sub.setTextColor(0xff666666);
  sub.setGravity(Gravity.CENTER);sub.setPadding(0,0,0,dp(18));root.addView(sub);
  add(root,"Refurbish","Change launcher display and style",0);
  add(root,"Explorer","Explore this app's files",1);
  add(root,"Monitor","Monitor selected app events",2);
  add(root,"Inspect Layout","Inspect the current interface",3);
  add(root,"Scan Agent","Start the base-process scan agent",4);
  add(root,"More","Additional modules",5);
 }
 private void add(LinearLayout root,String name,String detail,int action){
  LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(14),dp(10),dp(14),dp(10));
  GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);
  bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xff777777);row.setBackground(bg);
  Glyph glyph=new Glyph(this,action);
  LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(42),dp(42));
  gp.rightMargin=dp(14);row.addView(glyph,gp);
  LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);
  TextView h=new TextView(this);h.setText(name);h.setTextColor(Color.BLACK);
  h.setTextSize(16);h.setTypeface(null,Typeface.BOLD);labels.addView(h);
  TextView d=new TextView(this);d.setText(detail);d.setTextColor(0xff666666);
  d.setTextSize(12);labels.addView(d);
  row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  row.setOnClickListener(v->{
   if(action==0){startActivity(new Intent(this,ChangeDisplayActivity.class));return;}
   if(action==5){showMore();return;}
   String[] names={"","Explorer","Monitor","Inspect Layout","Scan Agent",""};
   new AlertDialog.Builder(this).setTitle(names[action])
     .setMessage("This module has not been connected yet.")
     .setPositiveButton(android.R.string.ok,null).show();
  });
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
  lp.bottomMargin=dp(10);root.addView(row,lp);
 }
 private void showMore(){
  java.util.List<VanceModuleRegistry.Entry> modules=VanceModuleRegistry.more();
  if(modules.isEmpty()){
   new AlertDialog.Builder(this).setTitle("More")
     .setMessage("No additional modules are registered yet.")
     .setPositiveButton(android.R.string.ok,null).show();
   return;
  }
  String[] titles=new String[modules.size()];
  for(int i=0;i<modules.size();i++)titles[i]=modules.get(i).title;
  new AlertDialog.Builder(this).setTitle("More")
    .setItems(titles,(dialog,which)->{
     if(which>=0&&which<modules.size()){
      Class<? extends Activity> target=modules.get(which).activity;
      startActivity(new Intent(this,target));
     }
    }).show();
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private static final class Glyph extends View {
  private final int type;private final Paint paint=new Paint(3);
  Glyph(Activity a,int type){super(a);this.type=type;paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.6f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/48f,getHeight()/48f);
   Path p=new Path();
   switch(type){
    case 0: p.moveTo(24,5);p.lineTo(42,15);p.lineTo(24,25);p.lineTo(6,15);p.close();canvas.drawPath(p,paint);canvas.drawLine(6,15,6,35,paint);canvas.drawLine(42,15,42,35,paint);canvas.drawLine(6,35,24,44,paint);canvas.drawLine(42,35,24,44,paint);canvas.drawLine(24,25,24,44,paint);break;
    case 1: p.moveTo(5,13);p.lineTo(19,13);p.lineTo(23,18);p.lineTo(43,18);p.lineTo(43,38);p.lineTo(5,38);p.close();canvas.drawPath(p,paint);canvas.drawLine(5,18,43,18,paint);break;
    case 2: canvas.drawRoundRect(5,7,43,34,3,3,paint);canvas.drawLine(17,41,31,41,paint);canvas.drawLine(24,34,24,41,paint);p.moveTo(10,27);p.lineTo(17,21);p.lineTo(23,25);p.lineTo(31,14);p.lineTo(38,18);canvas.drawPath(p,paint);break;
    case 3: canvas.drawRoundRect(7,5,37,42,2,2,paint);canvas.drawLine(13,13,31,13,paint);canvas.drawLine(13,19,31,19,paint);canvas.drawLine(13,25,25,25,paint);canvas.drawCircle(34,33,7,paint);canvas.drawLine(39,38,44,43,paint);break;
    case 4: canvas.drawRoundRect(12,12,36,36,4,4,paint);for(int i=0;i<4;i++){float q=16+i*5.3f;canvas.drawLine(q,5,q,12,paint);canvas.drawLine(q,36,q,43,paint);canvas.drawLine(5,q,12,q,paint);canvas.drawLine(36,q,43,q,paint);}canvas.drawCircle(24,24,4,paint);break;
    default: paint.setStyle(Paint.Style.FILL);canvas.drawCircle(10,24,3.2f,paint);canvas.drawCircle(24,24,3.2f,paint);canvas.drawCircle(38,24,3.2f,paint);paint.setStyle(Paint.Style.STROKE);
   }
   canvas.restore();
  }
 }
}