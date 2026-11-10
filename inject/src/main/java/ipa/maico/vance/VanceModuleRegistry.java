package ipa.maico.vance;
import android.app.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Core registry for additional Vance feature Activities. */
final class VanceModuleRegistry {
 static final class Entry {
  final String title;
  final Class<? extends Activity> activity;
  Entry(String title,Class<? extends Activity> activity){this.title=title;this.activity=activity;}
 }
 private static final List<Entry> MORE=new ArrayList<>();
 private VanceModuleRegistry(){}
 static void register(String title,Class<? extends Activity> activity){
  if(title==null||title.trim().isEmpty()||activity==null)throw new IllegalArgumentException("Invalid Vance module");
  for(Entry item:MORE)if(item.title.equals(title))throw new IllegalArgumentException("Duplicate Vance module: "+title);
  MORE.add(new Entry(title,activity));
 }
 static List<Entry> more(){return Collections.unmodifiableList(new ArrayList<>(MORE));}
}