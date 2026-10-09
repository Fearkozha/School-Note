package kz.school.journal;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

public class JournalTests extends Instrumentation {
    Bundle args;int checks=0;StringBuilder report=new StringBuilder();MainActivity a;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');Bundle progress=new Bundle();progress.putString("stream","PASS "+label+"\n");sendStatus(1,progress);}
    interface FailAction{void run() throws Exception;}
    void rejects(FailAction action,String label)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,label);}
    void main(Runnable run){final Throwable[] error={null};runOnMainSync(()->{try{run.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    void idle(){waitForIdleSync();try{getUiAutomation().waitForIdle(200,10000);}catch(Exception e){}SystemClock.sleep(250);}
    void screenshot(String name)throws Exception{idle();Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("screenshot");File dir=new File(getTargetContext().getExternalFilesDir(null),"verification");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    View tagged(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=tagged(g.getChildAt(i),tag);if(r!=null)return r;}}return null;}
    void clickableSize(View v){if(v.isClickable()&&!(v instanceof EditText)){check(v.getMeasuredHeight()>=a.dp(48),"touch target >=48dp: "+(v instanceof TextView?((TextView)v).getText():v.getClass().getSimpleName()));}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)clickableSize(g.getChildAt(i));}}
    AccessibilityNodeInfo find(AccessibilityNodeInfo n,String value,boolean byClass){if(n==null)return null;CharSequence item=byClass?n.getClassName():n.getText();if(item!=null&&item.toString().equals(value))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=find(n.getChild(i),value,byClass);if(child!=null)return child;}return null;}
    AccessibilityNodeInfo accessible(String value,boolean byClass){for(int retry=0;retry<20;retry++){idle();for(android.view.accessibility.AccessibilityWindowInfo w:getUiAutomation().getWindows()){AccessibilityNodeInfo root=w.getRoot();if(root!=null)root.refresh();AccessibilityNodeInfo found=find(root,value,byClass);if(found!=null)return found;}AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)root.refresh();AccessibilityNodeInfo found=find(root,value,byClass);if(found!=null)return found;SystemClock.sleep(250);}return null;}
    EditText firstInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText found=firstInput(g.getChildAt(i));if(found!=null)return found;}}return null;}
    TextView byText(View v,String label){if(v instanceof TextView&&((TextView)v).getText().toString().equals(label))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView found=byText(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
    void setFirstInput(String s){main(()->{if(a.currentEditor==null)throw new AssertionError("Editor not open");EditText field=firstInput(a.currentEditor.getWindow().getDecorView());if(field==null)throw new AssertionError("No editable input");field.setText(s);check(field.getText().toString().equals(s),"UI input "+s);});idle();}
    void clickText(String s){main(()->{View root=a.currentEditor==null?a.root:a.currentEditor.getWindow().getDecorView();TextView found=byText(root,s);if(found==null)throw new AssertionError("No button "+s);check(found.performClick(),"UI click "+s);});idle();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(info);
        if(args!=null&&"persist".equals(args.getString("phase"))){
            JournalDb saved=new JournalDb(getTargetContext());check(saved.classes().size()==2,"classes survive process restart");check(saved.rows("SELECT * FROM grades").size()==3,"grades survive process restart");check(saved.rows("SELECT * FROM attendance").size()==2,"attendance survives process restart");check(saved.setting("language","ru").equals("kk"),"language survives process restart");check(saved.setting("scale","12").equals("12"),"scale survives process restart");saved.close();
        }else{
            getTargetContext().deleteDatabase("journal.db");JournalDb db=new JournalDb(getTargetContext());
            long c1=db.named("classes",0,0,"7 А"),c2=db.named("classes",0,0,"8 Б");
            long s1=db.named("students",0,c1,"Айдана Сәрсен"),s2=db.named("students",0,c1,"Даниил Иванов"),s3=db.named("students",0,c2,"Марат Әли");
            long math=db.named("subjects",0,c1,"Математика"),lang=db.named("subjects",0,c1,"Қазақ тілі"),other=db.named("subjects",0,c2,"Физика");
            check(db.classes().size()==2&&db.students(c1).size()==2&&db.students(c2).size()==1,"multiple classes isolate students");check(db.subjects(c1).size()==2&&db.subjects(c2).size()==1,"subjects isolated by class");
            rejects(()->db.named("classes",0,0,"7 А"),"duplicate class rejected");rejects(()->db.named("subjects",0,c1,"Математика"),"duplicate subject rejected");rejects(()->db.named("students",0,c1,"  "),"blank names rejected");
            String today=LocalDate.now().toString(),before=LocalDate.now().minusDays(1).toString();
            long g=db.grade(0,s1,math,today,10,12,"Работа на уроке");db.grade(0,s1,math,before,5,5,"");
            check(db.grades(s1,math,today).size()==1&&db.grades(s1,math,null).size()==2,"grades retain dates and history");
            for(int maximum:new int[]{5,10,12,100,20}){long temp=db.grade(0,s2,math,today,maximum,maximum,"");check(db.grades(s2,math,today).get(0).i("max_value")==maximum,"grading scale "+maximum);db.delete("grades",temp);}
            rejects(()->db.grade(0,s1,math,today,13,12,""),"out-of-range grade rejected");rejects(()->db.grade(0,s1,math,today,0,12,""),"zero grade rejected");rejects(()->db.grade(0,s1,math,"2026-02-30",3,12,""),"invalid date rejected");rejects(()->db.grade(0,s3,math,today,8,12,""),"cross-class grade rejected");
            db.grade(g,s1,math,today,11,12,"Исправлено");check(db.grades(s1,math,today).get(0).i("value")==11,"grade editing preserves record");
            for(String status:new String[]{"present","absent","late","excused"}){db.attendance(s1,math,today,status);check(db.attendance(s1,math,today).equals(status),"attendance "+status);}
            check(db.rows("SELECT * FROM attendance").size()==1,"attendance update has no duplicate");db.attendance(s1,math,today,"");check(db.attendance(s1,math,today).isEmpty(),"attendance can be cleared");db.attendance(s1,math,today,"present");db.attendance(s2,math,today,"absent");
            rejects(()->db.attendance(s3,math,today,"present"),"cross-class attendance rejected");
            db.setting("scale","12",true);db.setting("language","ru",true);db.setting("lastClass",""+c1,true);db.setting("lastSubject",""+math,true);
            JSONObject backup=db.backup();db.close();check(db.grades(s1,math,null).size()==2,"SQLite data survives connection reopen");
            db.delete("classes",c1);check(db.students(c1).isEmpty()&&db.subjects(c1).isEmpty()&&db.rows("SELECT * FROM grades").isEmpty()&&db.rows("SELECT * FROM attendance").isEmpty(),"class deletion cascades related data");
            db.restore(backup);check(db.classes().size()==2&&db.grades(s1,math,null).size()==2&&db.rows("SELECT * FROM attendance").size()==2,"backup restores complete journal");
            JSONObject broken=new JSONObject(backup.toString());broken.getJSONArray("grades").getJSONObject(0).put("value",999);JournalDb current=db;rejects(()->current.restore(broken),"invalid backup rejected");check(db.grades(s1,math,null).size()==2,"invalid restore rolls back all deletions");
            JSONObject invalidSetting=new JSONObject(backup.toString());invalidSetting.getJSONArray("settings").put(new JSONObject().put("key","scale").put("value","1"));rejects(()->current.restore(invalidSetting),"invalid settings rollback");
            long tempStudent=db.named("students",0,c1,"Удаляемый ученик");db.grade(0,tempStudent,math,today,6,12,"");db.attendance(tempStudent,math,today,"late");db.delete("students",tempStudent);check(db.grades(tempStudent,math,null).isEmpty()&&db.attendance(tempStudent,math,today).isEmpty(),"student deletion cascades grades and attendance");
            long tempSubject=db.named("subjects",0,c1,"Удаляемый предмет");db.grade(0,s1,tempSubject,today,6,12,"");db.delete("subjects",tempSubject);check(db.grades(s1,tempSubject,null).isEmpty(),"subject deletion cascades grades");db.close();
            a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();main(()->clickableSize(a.root));screenshot("journal-ru");
            main(()->{View v=tagged(a.root,"grade-add-"+s2);if(v==null)throw new AssertionError("grade action missing");v.performClick();});idle();screenshot("grade-ru");setFirstInput("13");clickText("Сохранить");check(a.db.grades(s2,math,today).isEmpty(),"UI rejects grade above current maximum");setFirstInput("9");clickText("Сохранить");check(a.db.grades(s2,math,today).get(0).i("value")==9,"UI saves valid grade");
            main(()->{a.tab=1;a.render();});clickText("+ Добавить ученика");setFirstInput("Тестовый ученик");clickText("Сохранить");check(a.db.students(c1).size()==3,"UI creates student");long newStudent=a.db.students(c1).stream().filter(x->x.s("name").equals("Тестовый ученик")).findFirst().get().l("id");main(()->{a.db.delete("students",newStudent);a.tab=0;a.render();});
            main(()->{tagged(a.root,"attendance-"+s2).performClick();});clickText("Опоздал(а)");check(a.db.attendance(s2,math,today).equals("late"),"UI changes attendance");
            main(()->{a.saveScale(5);});check(a.db.grades(s1,math,today).get(0).i("max_value")==12,"scale change preserves original grade denominator");main(()->{a.saveScale(12);a.changeLanguage(true);});check(a.kk&&a.db.setting("language","ru").equals("kk"),"Kazakh language selection saved");screenshot("journal-kk");
            main(()->{a.tab=3;a.render();});screenshot("settings-kk");main(()->{a.tab=0;a.render();});
            JSONObject finalBackup=a.db.backup();File dir=new File(getTargetContext().getExternalFilesDir(null),"verification");try(FileOutputStream out=new FileOutputStream(new File(dir,"tested-backup.json"))){out.write(finalBackup.toString(2).getBytes("UTF-8"));}
            main(()->a.finish());
        }
        result.putString("stream","\nOK: "+checks+" checks passed\n"+report);finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL after "+checks+" checks: "+error+"\n"+android.util.Log.getStackTraceString(error)+"\n"+report);finish(Activity.RESULT_CANCELED,result);}}
}
