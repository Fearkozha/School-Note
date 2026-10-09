package kz.school.journal;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final int GREEN=Color.rgb(65,89,221),INK=Color.rgb(27,32,61),MUTED=Color.rgb(98,108,130),PALE=Color.rgb(241,244,255),LINE=Color.rgb(228,233,245),RED=Color.rgb(168,44,42);
    JournalDb db;
    boolean kk=false;
    long classId=0,subjectId=0;
    int tab=0,scale=12;
    LocalDate day=LocalDate.now();
    LinearLayout root,body;
    AlertDialog currentEditor;
    final String[] statuses={"present","absent","late","excused",""};
    @Override public void onCreate(Bundle state){
        db=new JournalDb(this);kk=db.setting("language","ru").equals("kk");scale=Integer.parseInt(db.setting("scale","12"));
        setLocale();super.onCreate(state);
        classId=Long.parseLong(db.setting("lastClass","0"));subjectId=Long.parseLong(db.setting("lastSubject","0"));
        if(state!=null){tab=state.getInt("tab");classId=state.getLong("class");subjectId=state.getLong("subject");day=LocalDate.parse(state.getString("day",LocalDate.now().toString()));}
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    void setLocale(){Locale locale=new Locale(kk?"kk":"ru");Locale.setDefault(locale);Configuration c=new Configuration(getResources().getConfiguration());c.setLocale(locale);getResources().updateConfiguration(c,getResources().getDisplayMetrics());}
    @Override public void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putInt("tab",tab);s.putLong("class",classId);s.putLong("subject",subjectId);s.putString("day",day.toString());}
    @Override public void onDestroy(){super.onDestroy();if(currentEditor!=null)currentEditor.dismiss();if(db!=null)db.close();}
    String t(String ru,String kz){return kk?kz:ru;}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    GradientDrawable bg(int color,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(20));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    android.graphics.drawable.Drawable touchBg(int color,int stroke){return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x224159DD),bg(color,stroke),bg(Color.WHITE,0));}
    TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    TextView button(String label,boolean primary,Runnable click){TextView v=text(label,17,primary?Color.WHITE:GREEN,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(14),dp(12),dp(14),dp(12));v.setMinHeight(dp(56));v.setBackground(touchBg(primary?GREEN:PALE,primary?0:LINE));v.setOnClickListener(x->{x.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);click.run();});v.setFocusable(true);v.setContentDescription(label);return v;}
    void add(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    void gap(LinearLayout p,int n){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    void weighted(LinearLayout r,View v,float weight){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,weight);r.addView(v,p);}
    void hgap(LinearLayout r,int n){r.addView(new View(this),new LinearLayout.LayoutParams(dp(n),1));}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(bg(Color.WHITE,LINE));return c;}
    void section(String heading){gap(body,18);add(body,text(heading,22,INK,true));gap(body,12);}
    void hint(LinearLayout p,String s){TextView h=text(s,15,MUTED,false);h.setLineSpacing(dp(3),1);add(p,h);}
    String date(LocalDate d){return d.format(DateTimeFormatter.ofPattern("dd MMMM yyyy",new Locale(kk?"kk":"ru")));}
    void toast(String ru,String kz){Toast.makeText(this,t(ru,kz),Toast.LENGTH_LONG).show();}
    void reconcile(){
        List<JournalDb.Row> cs=db.classes();if(cs.stream().noneMatch(c->c.l("id")==classId))classId=cs.isEmpty()?0:cs.get(0).l("id");
        List<JournalDb.Row> ss=db.subjects(classId);if(ss.stream().noneMatch(s->s.l("id")==subjectId))subjectId=ss.isEmpty()?0:ss.get(0).l("id");
        db.setting("lastClass",""+classId,true);db.setting("lastSubject",""+subjectId,true);
    }
    String selectedName(List<JournalDb.Row> list,long id){for(JournalDb.Row r:list)if(r.l("id")==id)return r.s("name");return "";}
    void render(){
        reconcile();root=column();root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=column();header.setPadding(dp(20),dp(18),dp(20),dp(10));
        LinearLayout brand=row();TextView monogram=text("SN",20,Color.WHITE,true);monogram.setGravity(Gravity.CENTER);monogram.setBackground(bg(GREEN,0));brand.addView(monogram,new LinearLayout.LayoutParams(dp(48),dp(48)));hgap(brand,12);LinearLayout heading=column();add(heading,text("School Note",28,INK,true));hint(heading,t("Школьный журнал · офлайн","Мектеп журналы · офлайн"));weighted(brand,heading,1);add(header,brand);add(root,header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();body.setPadding(dp(16),dp(4),dp(16),dp(24));scroll.addView(body);
        if(tab!=3){classChooser();if(classId==0)welcome();else if(tab==0)journal();else if(tab==1)people();else subjects();}else settings();
        LinearLayout nav=row();nav.setPadding(dp(6),dp(8),dp(6),dp(8));nav.setBackgroundColor(Color.WHITE);String[] labels={t("Журнал","Журнал"),t("Ученики","Оқушылар"),t("Предметы","Пәндер"),t("Настройки","Баптау")};
        int[] icons={R.drawable.ic_book,R.drawable.ic_people,R.drawable.ic_subjects,R.drawable.ic_settings};
        for(int i=0;i<4;i++){final int n=i;TextView b=text(labels[i],14,i==tab?GREEN:MUTED,i==tab);b.setGravity(Gravity.CENTER);b.setPadding(dp(2),dp(8),dp(2),dp(8));b.setMinHeight(dp(68));Drawable icon=getDrawable(icons[i]).mutate();icon.setTint(i==tab?GREEN:MUTED);b.setCompoundDrawablesWithIntrinsicBounds(null,icon,null,null);b.setCompoundDrawablePadding(dp(5));b.setBackground(touchBg(i==tab?PALE:Color.WHITE,0));b.setContentDescription(labels[i]);b.setFocusable(true);b.setOnClickListener(v->{tab=n;render();});weighted(nav,b,1);}
        add(root,nav);
    }
    void classChooser(){
        List<JournalDb.Row> cs=db.classes();LinearLayout r=row();
        TextView b=button(classId==0?t("Выбрать класс","Сыныпты таңдау"):t("Класс ","Сынып ")+selectedName(cs,classId)+"  ▾",false,()->{
            if(cs.isEmpty()){editName("classes",null);return;}String[] names=new String[cs.size()+1];for(int i=0;i<cs.size();i++)names[i]=cs.get(i).s("name");names[cs.size()]=t("+ Добавить класс","+ Сынып қосу");
            listDialog(t("Ваши классы","Сыныптарыңыз"),names,n->{if(n==cs.size())editName("classes",null);else{classId=cs.get(n).l("id");subjectId=0;render();}});
        });weighted(r,b,1);hgap(r,8);TextView manage=button(t("Классы","Сыныптар"),false,this::manageClasses);r.addView(manage);add(body,r);
    }
    void welcome(){
        gap(body,22);LinearLayout c=card();add(c,text(t("Всё для вашего класса","Сыныбыңызға арналған журнал"),24,INK,true));gap(c,12);
        hint(c,t("Добавьте класс, учеников и предметы. Затем выставляйте оценки и отмечайте посещаемость на выбранную дату.","Сынып, оқушылар және пәндер қосыңыз. Содан кейін таңдалған күнге баға қойып, қатысуды белгілеңіз."));gap(c,18);
        add(c,button(t("+ Создать первый класс","+ Алғашқы сыныпты құру"),true,()->editName("classes",null)));gap(c,16);hint(c,t("Русский / Қазақша · шкала 1–"+scale,"Русский / Қазақша · 1–"+scale+" балдық жүйе"));add(body,c);
    }
    void manageClasses(){
        LinearLayout c=dialogBody();List<JournalDb.Row> cs=db.classes();final AlertDialog[] box={null};
        for(JournalDb.Row cl:cs){add(c,button(cl.s("name"),false,()->{box[0].dismiss();editName("classes",cl);}));gap(c,8);}
        add(c,button(t("+ Добавить класс","+ Сынып қосу"),true,()->{box[0].dismiss();editName("classes",null);}));
        box[0]=new AlertDialog.Builder(this).setTitle(t("Классы · нажмите для изменения","Сыныптар · өзгерту үшін басыңыз")).setView(scrolled(c)).setNegativeButton(t("Закрыть","Жабу"),null).create();showDialog(box[0]);
    }
    void journal(){
        gap(body,12);List<JournalDb.Row> ss=db.subjects(classId);
        add(body,button(subjectId==0?t("+ Добавить предмет","+ Пән қосу"):selectedName(ss,subjectId)+"  ▾",false,()->{
            if(ss.isEmpty()){editName("subjects",null);return;}String[] names=new String[ss.size()];for(int i=0;i<ss.size();i++)names[i]=ss.get(i).s("name");listDialog(t("Выберите предмет","Пәнді таңдаңыз"),names,n->{subjectId=ss.get(n).l("id");render();});
        }));gap(body,10);LinearLayout dates=row();
        TextView prev=button("‹",false,()->{day=day.minusDays(1);render();});prev.setContentDescription(t("Предыдущий день","Алдыңғы күн"));dates.addView(prev,new LinearLayout.LayoutParams(dp(52),-2));hgap(dates,6);
        TextView dateButton=button(date(day),false,()->pickDate(day,d->{day=d;render();}));dateButton.setTextSize(16);weighted(dates,dateButton,1);hgap(dates,6);
        TextView next=button("›",false,()->{day=day.plusDays(1);render();});next.setContentDescription(t("Следующий день","Келесі күн"));dates.addView(next,new LinearLayout.LayoutParams(dp(52),-2));add(body,dates);
        if(!day.equals(LocalDate.now())){gap(body,6);add(body,button(t("Перейти к сегодня","Бүгінгі күнге өту"),false,()->{day=LocalDate.now();render();}));}
        List<JournalDb.Row> students=db.students(classId);if(subjectId>0&&!students.isEmpty())dailySummary(students);section(t("Ученики · ","Оқушылар · ")+students.size());
        hint(body,t("Оценки 1–"+scale+" · имя ученика открывает историю","1–"+scale+" балл · тарих үшін оқушының атын басыңыз"));gap(body,12);
        if(students.isEmpty()){empty(t("Пока нет учеников","Оқушылар әлі жоқ"),t("Добавьте список вашего класса.","Сыныбыңыздың тізімін қосыңыз."),t("+ Добавить ученика","+ Оқушы қосу"),()->editName("students",null));return;}
        if(subjectId==0){empty(t("Нужен предмет","Пән қосу қажет"),t("Для оценок и посещаемости выберите предмет.","Бағалар мен қатысуды белгілеу үшін пән таңдаңыз."),t("+ Добавить предмет","+ Пән қосу"),()->editName("subjects",null));return;}
        for(JournalDb.Row student:students){LinearLayout c=card();long id=student.l("id");LinearLayout identity=row();int[] avatarColors={0xFFE9EDFF,0xFFF2EAFE,0xFFE2F6FA,0xFFFFF0E1};TextView avatar=text(initials(student.s("name")),18,GREEN,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(bg(avatarColors[(int)(id%4)],0));identity.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));hgap(identity,12);TextView name=text(student.s("name")+"  ›",20,INK,true);name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);name.setBackground(touchBg(Color.WHITE,0));name.setFocusable(true);name.setOnClickListener(v->history(student));weighted(identity,name,1);add(c,identity);gap(c,8);
            List<JournalDb.Row> all=db.grades(id,subjectId,null);double avg=0;for(JournalDb.Row g:all)avg+=(double)g.i("value")/g.i("max_value");
            hint(c,all.isEmpty()?t("Оценок ещё нет","Бағалар әлі жоқ"):t("Средний балл: ","Орташа балл: ")+String.format(Locale.getDefault(),"%.1f / %d",avg/all.size()*scale,scale));gap(c,12);
            List<JournalDb.Row> marks=db.grades(id,subjectId,day.toString());
            if(marks.isEmpty())hint(c,t("На эту дату оценок нет","Бұл күнге баға қойылмаған"));else{HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout markRow=row();for(JournalDb.Row g:marks){TextView b=button(g.s("value")+"/"+g.s("max_value"),false,()->gradeEditor(student,g,null));double ratio=(double)g.i("value")/g.i("max_value");b.setBackground(touchBg(ratio>=0.8?0xFFE9EDFF:ratio>=0.5?0xFFE2F6FA:0xFFFFF0E1,0));b.setTextColor(ratio>=0.8?GREEN:ratio>=0.5?0xFF0F7782:0xFF97520D);markRow.addView(b);hgap(markRow,8);}hs.addView(markRow);add(c,hs);}
            gap(c,12);LinearLayout actions=row();TextView grade=button(t("+ Оценка","+ Баға"),true,()->gradeEditor(student,null,null));grade.setTag("grade-add-"+id);weighted(actions,grade,1);hgap(actions,8);
            String status=db.attendance(id,subjectId,day.toString());TextView attendance=button(statusLabel(status),false,()->attendancePicker(student,day));attendance.setTextSize(15);attendance.setTag("attendance-"+id);weighted(actions,attendance,1);add(c,actions);add(body,c);gap(body,12);
        }
    }
    String initials(String name){String[] parts=name.trim().split("\\s+");String out="";for(int i=0;i<Math.min(2,parts.length);i++)if(!parts[i].isEmpty())out+=parts[i].substring(0,1);return out.toUpperCase(Locale.getDefault());}
    void dailySummary(List<JournalDb.Row> students){
        int grades=0,present=0;for(JournalDb.Row s:students){grades+=db.grades(s.l("id"),subjectId,day.toString()).size();String status=db.attendance(s.l("id"),subjectId,day.toString());if(status.equals("present")||status.equals("late"))present++;}
        gap(body,14);LinearLayout stats=row();String[] labels={t("Учеников","Оқушы"),t("Оценок","Баға"),t("На уроке","Сабақта")};int[] nums={students.size(),grades,present},colors={GREEN,Color.rgb(113,68,194),Color.rgb(15,119,130)};
        for(int i=0;i<3;i++){LinearLayout cell=column();cell.setPadding(dp(12),dp(12),dp(8),dp(12));cell.setBackground(bg(i==1?0xFFF5F0FF:i==2?0xFFEAF9FB:PALE,0));add(cell,text(""+nums[i],26,colors[i],true));add(cell,text(labels[i],14,MUTED,false));weighted(stats,cell,1);if(i<2)hgap(stats,8);}add(body,stats);
    }
    String statusLabel(String s){switch(s){case "present":return t("Присутствует","Қатысты");case "absent":return t("Отсутствует","Қатыспады");case "late":return t("Опоздал(а)","Кешікті");case "excused":return t("Уваж. причина","Себепті");default:return t("Посещаемость","Қатысу");}}
    void attendancePicker(JournalDb.Row student,LocalDate when){String[] labels=new String[5];for(int i=0;i<4;i++)labels[i]=statusLabel(statuses[i]);labels[4]=t("Снять отметку","Белгіні алып тастау");listDialog(student.s("name")+"\n"+date(when),labels,n->{db.attendance(student.l("id"),subjectId,when.toString(),statuses[n]);render();});}
    void people(){section(t("Ученики","Оқушылар"));add(body,button(t("+ Добавить ученика","+ Оқушы қосу"),true,()->editName("students",null)));gap(body,14);
        List<JournalDb.Row> list=db.students(classId);if(list.isEmpty()){hint(body,t("Список пока пуст. Добавьте первого ученика.","Тізім бос. Алғашқы оқушыны қосыңыз."));return;}
        EditText search=input(t("Поиск по имени","Аты бойынша іздеу"),false,120);add(body,search);gap(body,12);LinearLayout items=column();add(body,items);
        Runnable fill=()->{items.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.getDefault()).trim();for(JournalDb.Row s:list){if(!s.s("name").toLowerCase(Locale.getDefault()).contains(q))continue;add(items,button(s.s("name")+"  ›",false,()->editName("students",s)));gap(items,10);}};fill.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){fill.run();}public void afterTextChanged(Editable e){}});
        gap(body,8);hint(body,t("Нажмите на ученика, чтобы изменить имя или удалить.","Атын өзгерту немесе жою үшін оқушыны басыңыз."));
    }
    void subjects(){section(t("Предметы класса","Сынып пәндері"));add(body,button(t("+ Добавить предмет","+ Пән қосу"),true,()->editName("subjects",null)));gap(body,14);for(JournalDb.Row s:db.subjects(classId)){add(body,button(s.s("name")+"  ›",false,()->editName("subjects",s)));gap(body,10);}hint(body,t("Нажмите на предмет, чтобы изменить название или удалить.","Атауын өзгерту немесе жою үшін пәнді басыңыз."));}
    void empty(String title,String detail,String action,Runnable run){LinearLayout c=card();add(c,text(title,22,INK,true));gap(c,10);hint(c,detail);gap(c,16);add(c,button(action,true,run));add(body,c);}
    EditText input(String hint,boolean numeric,int limit){EditText e=new EditText(this);e.setTextSize(19);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setSingleLine(true);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setMinHeight(dp(58));e.setBackground(bg(Color.WHITE,LINE));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});e.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);return e;}
    LinearLayout dialogBody(){LinearLayout c=column();c.setPadding(dp(20),dp(14),dp(20),dp(12));return c;}
    ScrollView scrolled(View v){ScrollView s=new ScrollView(this);s.addView(v);return s;}
    interface IndexAction{void go(int n);}
    void showDialog(AlertDialog dialog){currentEditor=dialog;dialog.setOnDismissListener(d->{if(currentEditor==dialog)currentEditor=null;});dialog.show();}
    void listDialog(String title,String[] labels,IndexAction action){LinearLayout c=dialogBody();final AlertDialog[] box={null};for(int i=0;i<labels.length;i++){final int n=i;add(c,button(labels[i],false,()->{box[0].dismiss();action.go(n);}));gap(c,8);}box[0]=new AlertDialog.Builder(this).setTitle(title).setView(scrolled(c)).setNegativeButton(t("Отмена","Бас тарту"),null).create();showDialog(box[0]);}
    void editName(String table,JournalDb.Row existing){
        String kind=table.equals("classes")?t("класс","сынып"):table.equals("students")?t("ученика","оқушы"):t("предмет","пән");
        LinearLayout c=dialogBody();EditText e=input(table.equals("classes")?t("Например, 7 А","Мысалы, 7 А"):table.equals("students")?t("Имя и фамилия","Аты-жөні"):t("Название предмета","Пән атауы"),false,table.equals("students")?120:80);if(existing!=null)e.setText(existing.s("name"));add(c,e);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle((existing==null?t("Добавить ","Қосу: "):t("Изменить ","Өзгерту: "))+kind).setView(c).setPositiveButton(t("Сохранить","Сақтау"),null).setNegativeButton(t("Отмена","Бас тарту"),null);
        if(existing!=null)b.setNeutralButton(t("Удалить","Жою"),(d,w)->confirmDelete(table,existing));AlertDialog dialog=b.create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String value=e.getText().toString().trim();if(value.isEmpty()){e.setError(t("Введите название или имя","Атауын немесе атын енгізіңіз"));return;}
            try{long id=db.named(table,existing==null?0:existing.l("id"),classId,value);if(table.equals("classes")){classId=id;subjectId=0;}if(table.equals("subjects"))subjectId=id;dialog.dismiss();hideKeyboard(e);render();}catch(android.database.sqlite.SQLiteConstraintException ex){e.setError(t("Такое название уже есть","Мұндай атау бар"));}
        }));showDialog(dialog);e.requestFocus();
    }
    void hideKeyboard(View v){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    void confirmDelete(String table,JournalDb.Row item){new AlertDialog.Builder(this).setTitle(t("Удалить «","«")+item.s("name")+t("»?","» жойылсын ба?"))
        .setMessage(t("Связанные оценки и отметки посещаемости также будут удалены. Действие нельзя отменить.","Байланысты бағалар мен қатысу белгілері де жойылады. Бұл әрекетті қайтару мүмкін емес."))
        .setNegativeButton(t("Отмена","Бас тарту"),null).setPositiveButton(t("Удалить","Жою"),(d,w)->{db.delete(table,item.l("id"));render();}).show();}
    interface DateAction{void go(LocalDate d);}
    void pickDate(LocalDate value,DateAction action){new DatePickerDialog(this,(v,y,m,d)->action.go(LocalDate.of(y,m+1,d)),value.getYear(),value.getMonthValue()-1,value.getDayOfMonth()).show();}
    void gradeEditor(JournalDb.Row student,JournalDb.Row existing,Runnable after){
        final int maximum=existing==null?scale:existing.i("max_value");final LocalDate[] chosen={existing==null?day:LocalDate.parse(existing.s("day"))};
        LinearLayout c=dialogBody();hint(c,student.s("name")+" · "+selectedName(db.subjects(classId),subjectId));gap(c,12);
        final TextView[] dateView={null};dateView[0]=button(date(chosen[0]),false,()->pickDate(chosen[0],d->{chosen[0]=d;dateView[0].setText(date(d));}));add(c,dateView[0]);gap(c,12);
        EditText value=input(t("Балл от 1 до ","1-ден ")+maximum+t("","-ге дейінгі балл"),true,3);if(existing!=null)value.setText(existing.s("value"));add(c,value);gap(c,10);
        if(maximum<=12){for(int first=1;first<=maximum;first+=4){LinearLayout choices=row();for(int col=0;col<4;col++){final int score=first+col;if(score<=maximum){TextView chip=button(""+score,false,()->value.setText(""+score));weighted(choices,chip,1);}else weighted(choices,new View(this),1);if(col<3)hgap(choices,6);}add(c,choices);gap(c,6);}gap(c,6);}
        EditText note=input(t("Комментарий (необязательно)","Түсініктеме (міндетті емес)"),false,500);if(existing!=null)note.setText(existing.s("note"));add(c,note);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?t("Новая оценка","Жаңа баға"):t("Изменить оценку","Бағаны өзгерту")).setView(scrolled(c)).setPositiveButton(t("Сохранить","Сақтау"),null).setNegativeButton(t("Отмена","Бас тарту"),null);
        if(existing!=null)b.setNeutralButton(t("Удалить","Жою"),(d,w)->new AlertDialog.Builder(this).setTitle(t("Удалить оценку?","Баға жойылсын ба?")).setNegativeButton(t("Отмена","Бас тарту"),null).setPositiveButton(t("Удалить","Жою"),(a,z)->{db.delete("grades",existing.l("id"));render();if(after!=null)after.run();}).show());
        AlertDialog dialog=b.create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{int score;try{score=Integer.parseInt(value.getText().toString());}catch(Exception ex){score=0;}if(score<1||score>maximum){value.setError(t("Введите балл 1–","1–")+maximum+t(""," аралығындағы балды енгізіңіз"));return;}
            db.grade(existing==null?0:existing.l("id"),student.l("id"),subjectId,chosen[0].toString(),score,maximum,note.getText().toString());dialog.dismiss();hideKeyboard(value);render();if(after!=null)after.run();}));showDialog(dialog);
    }
    void history(JournalDb.Row student){
        LinearLayout c=dialogBody();String subject=selectedName(db.subjects(classId),subjectId);hint(c,subject);gap(c,12);
        List<JournalDb.Row> grades=db.grades(student.l("id"),subjectId,null);add(c,text(t("Все оценки","Барлық бағалар"),20,INK,true));gap(c,8);
        final AlertDialog[] dialog={null};if(grades.isEmpty())hint(c,t("Оценок ещё нет","Бағалар әлі жоқ"));
        for(JournalDb.Row g:grades){String label=g.s("value")+"/"+g.s("max_value")+" · "+date(LocalDate.parse(g.s("day")))+(g.s("note").isEmpty()?"":"\n"+g.s("note"));add(c,button(label,false,()->{dialog[0].dismiss();gradeEditor(student,g,()->history(student));}));gap(c,8);}
        gap(c,12);add(c,text(t("История посещаемости","Қатысу тарихы"),20,INK,true));gap(c,8);
        List<JournalDb.Row> attendance=db.rows("SELECT * FROM attendance WHERE student_id=? AND subject_id=? ORDER BY day DESC",""+student.l("id"),""+subjectId);
        if(attendance.isEmpty())hint(c,t("Отметок ещё нет","Белгілер әлі жоқ"));for(JournalDb.Row a:attendance){LocalDate d=LocalDate.parse(a.s("day"));add(c,button(date(d)+" · "+statusLabel(a.s("status")),false,()->{dialog[0].dismiss();attendancePicker(student,d);}));gap(c,8);}
        dialog[0]=new AlertDialog.Builder(this).setTitle(student.s("name")).setView(scrolled(c)).setNegativeButton(t("Закрыть","Жабу"),null).create();showDialog(dialog[0]);
    }
    void settings(){
        section(t("Настройки","Баптау"));LinearLayout language=card();add(language,text(t("Язык интерфейса","Интерфейс тілі"),21,INK,true));gap(language,12);LinearLayout choices=row();
        weighted(choices,button("Русский",!kk,()->changeLanguage(false)),1);hgap(choices,8);weighted(choices,button("Қазақша",kk,()->changeLanguage(true)),1);add(language,choices);add(body,language);gap(body,14);
        LinearLayout scores=card();add(scores,text(t("Система оценок","Бағалау жүйесі"),21,INK,true));gap(scores,12);add(scores,button(t("От 1 до ","1-ден ")+scale+t(" баллов  ▾"," балға дейін  ▾"),false,this::chooseScale));gap(scores,10);
        hint(scores,t("Шкала применяется к новым оценкам во всех классах. Старые оценки сохраняют свою шкалу. Средний балл пересчитывается пропорционально.","Жүйе барлық сыныптағы жаңа бағаларға қолданылады. Бұрынғы бағалар өз жүйесін сақтайды. Орташа балл пропорционалды қайта есептеледі."));add(body,scores);gap(body,14);
        LinearLayout copy=card();add(copy,text(t("Резервная копия","Сақтық көшірме"),21,INK,true));gap(copy,10);hint(copy,t("Сохраните журнал в файл или восстановите его на другом устройстве. Файл содержит имена, оценки и посещаемость.","Журналды файлға сақтаңыз немесе басқа құрылғыда қалпына келтіріңіз. Файлда аты-жөндер, бағалар және қатысу деректері болады."));gap(copy,14);
        add(copy,button(t("Сохранить в файл","Файлға сақтау"),true,this::exportBackup));gap(copy,8);add(copy,button(t("Восстановить из файла","Файлдан қалпына келтіру"),false,this::importBackup));add(body,copy);gap(body,14);
        LinearLayout info=card();add(info,text(t("Ваши данные — на телефоне","Деректеріңіз телефонда сақталады"),21,INK,true));gap(info,10);
        hint(info,t("Приложение работает без интернета, аккаунтов и рекламы. После закрытия или перезагрузки данные сохраняются. При удалении приложения данные удаляются: заранее сделайте резервную копию.","Қолданба интернетсіз, аккаунтсыз және жарнамасыз жұмыс істейді. Жабылғаннан немесе қайта жүктелгеннен кейін деректер сақталады. Қолданбаны жойсаңыз, деректер де жойылады: алдын ала сақтық көшірме жасаңыз."));gap(info,10);hint(info,t("School Note 1.1.1 · Android 8.0 и новее","School Note 1.1.1 · Android 8.0 және одан кейінгі нұсқалар"));gap(info,14);add(info,text("Сделано Айқожа Умар",18,GREEN,true));add(body,info);
    }
    void changeLanguage(boolean value){kk=value;db.setting("language",kk?"kk":"ru",true);setLocale();render();}
    void chooseScale(){String[] labels={t("5 баллов","5 балл"),t("10 баллов","10 балл"),t("12 баллов","12 балл"),t("100 баллов","100 балл"),t("Свой максимум…","Өз максимумыңыз…")};int[] values={5,10,12,100};listDialog(t("Выберите систему оценок","Бағалау жүйесін таңдаңыз"),labels,n->{if(n<4){saveScale(values[n]);return;}
        LinearLayout c=dialogBody();EditText max=input(t("Максимум от 2 до 100","2-ден 100-ге дейінгі максимум"),true,3);max.setText(""+scale);add(c,max);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("Своя шкала","Өз бағалау жүйеңіз")).setView(c).setNegativeButton(t("Отмена","Бас тарту"),null).setPositiveButton(t("Сохранить","Сақтау"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{int number;try{number=Integer.parseInt(max.getText().toString());}catch(Exception ex){number=0;}if(number<2||number>100){max.setError(t("Укажите число от 2 до 100","2-ден 100-ге дейінгі санды енгізіңіз"));return;}dialog.dismiss();saveScale(number);}));showDialog(dialog);
    });}
    void saveScale(int value){scale=value;db.setting("scale",""+scale,true);render();}
    void exportBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"school-journal-"+LocalDate.now()+".json");try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){toast("На устройстве нет приложения для сохранения файлов","Құрылғыда файлдарды сақтайтын қолданба жоқ");}}
    void importBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,11);}catch(ActivityNotFoundException e){toast("На устройстве нет приложения для выбора файлов","Құрылғыда файлдарды таңдайтын қолданба жоқ");}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{if(request==10){byte[] content=db.backup().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException();out.write(content);}toast("Резервная копия сохранена","Сақтық көшірме сақталды");}
            else if(request==11){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>8*1024*1024)throw new IOException("size");}}
                JSONObject backup=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));if(!backup.optString("format").equals("school-journal")||backup.optInt("version")!=1)throw new JSONException("format");
                new AlertDialog.Builder(this).setTitle(t("Заменить текущий журнал?","Қазіргі журнал ауыстырылсын ба?"))
                    .setMessage(t("В файле классов: ","Файлдағы сынып саны: ")+backup.getJSONArray("classes").length()+t("; учеников: ","; оқушы саны: ")+backup.getJSONArray("students").length()+t(". Текущие данные будут заменены. Сначала сохраните их копию, если они нужны.",". Қазіргі деректер ауыстырылады. Қажет болса, алдымен олардың көшірмесін сақтаңыз."))
                    .setNegativeButton(t("Отмена","Бас тарту"),null).setPositiveButton(t("Восстановить","Қалпына келтіру"),(d,w)->{try{db.restore(backup);kk=db.setting("language","ru").equals("kk");scale=Integer.parseInt(db.setting("scale","12"));setLocale();classId=0;subjectId=0;render();toast("Журнал восстановлен","Журнал қалпына келтірілді");}catch(Exception ex){toast("Не удалось восстановить файл. Текущий журнал сохранён.","Файлды қалпына келтіру мүмкін болмады. Қазіргі журнал сақталды.");}}).show();
            }
        }catch(Exception ex){toast("Не удалось открыть или сохранить файл журнала","Журнал файлын ашу немесе сақтау мүмкін болмады");}
    }
}
