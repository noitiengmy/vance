package com.maico.refurbished;
import android.app.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registration point for future modules shown by the dashboard's More entry. */
final class ModuleRegistry {
 static final class Entry {
  final String title;
  final Class<? extends Activity> activity;
  Entry(String title,Class<? extends Activity> activity){this.title=title;this.activity=activity;}
 }
 private static final List<Entry> MORE=new ArrayList<>();
 private ModuleRegistry(){}
 static void registerMore(String title,Class<? extends Activity> activity){
  if(title==null||title.trim().isEmpty()||activity==null)throw new IllegalArgumentException("Invalid module");
  for(Entry entry:MORE)if(entry.title.equals(title))throw new IllegalArgumentException("Duplicate module: "+title);
  MORE.add(new Entry(title,activity));
 }
 static List<Entry> moreModules(){return Collections.unmodifiableList(new ArrayList<>(MORE));}
}