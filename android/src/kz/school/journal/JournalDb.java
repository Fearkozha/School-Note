package kz.school.journal;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import java.time.LocalDate;

public final class JournalDb extends SQLiteOpenHelper {
    public static final String[] TABLES={"classes","students","subjects","grades","attendance","settings"};
    public static final class Row {
        private final Map<String,String> values=new HashMap<>();
        public String s(String k){String v=values.get(k);return v==null?"":v;}
        public long l(String k){return Long.parseLong(s(k));}
        public int i(String k){return Integer.parseInt(s(k));}
    }
    public JournalDb(Context c){super(c,"journal.db",null,1);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE classes(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE CHECK(length(trim(name)) BETWEEN 1 AND 80))");
        d.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT,class_id INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 120))");
        d.execSQL("CREATE TABLE subjects(id INTEGER PRIMARY KEY AUTOINCREMENT,class_id INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 80),UNIQUE(class_id,name))");
        d.execSQL("CREATE TABLE grades(id INTEGER PRIMARY KEY AUTOINCREMENT,student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,day TEXT NOT NULL,value INTEGER NOT NULL,max_value INTEGER NOT NULL CHECK(max_value BETWEEN 2 AND 100),note TEXT NOT NULL DEFAULT '',CHECK(value BETWEEN 1 AND max_value))");
        d.execSQL("CREATE TABLE attendance(id INTEGER PRIMARY KEY AUTOINCREMENT,student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,day TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('present','absent','late','excused')),UNIQUE(student_id,subject_id,day))");
        d.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        d.execSQL("CREATE INDEX student_class ON students(class_id)");
        d.execSQL("CREATE INDEX subject_class ON subjects(class_id)");
        d.execSQL("CREATE INDEX grade_lookup ON grades(student_id,subject_id,day)");
        for(String table:new String[]{"grades","attendance"}){
            for(String event:new String[]{"INSERT","UPDATE"}) d.execSQL("CREATE TRIGGER "+table+"_same_class_"+event+" BEFORE "+event+" ON "+table+" WHEN (SELECT class_id FROM students WHERE id=NEW.student_id) != (SELECT class_id FROM subjects WHERE id=NEW.subject_id) BEGIN SELECT RAISE(ABORT,'class mismatch'); END");
        }
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next){throw new IllegalStateException("Unsupported schema");}
    public List<Row> rows(String sql,String... args){
        List<Row> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext()){Row r=new Row();for(int i=0;i<c.getColumnCount();i++)r.values.put(c.getColumnName(i),c.isNull(i)?"":c.getString(i));out.add(r);}
        }return out;
    }
    public String setting(String key,String fallback){List<Row> r=rows("SELECT value FROM settings WHERE key=?",key);return r.isEmpty()?fallback:r.get(0).s("value");}
    public void setting(String key,String value,boolean write){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public List<Row> classes(){return rows("SELECT * FROM classes ORDER BY name COLLATE NOCASE,id");}
    public List<Row> students(long cid){return rows("SELECT * FROM students WHERE class_id=? ORDER BY name COLLATE NOCASE,id",""+cid);}
    public List<Row> subjects(long cid){return rows("SELECT * FROM subjects WHERE class_id=? ORDER BY name COLLATE NOCASE,id",""+cid);}
    public long named(String table,long id,long cid,String name){
        if(!Arrays.asList("classes","students","subjects").contains(table))throw new IllegalArgumentException();
        name=name.trim();if(name.isEmpty()||name.length()>(table.equals("students")?120:80))throw new IllegalArgumentException("name");
        ContentValues v=new ContentValues();v.put("name",name);if(!table.equals("classes"))v.put("class_id",cid);
        if(id>0){if(getWritableDatabase().update(table,v,"id=?",new String[]{""+id})!=1)throw new IllegalArgumentException();return id;}
        return getWritableDatabase().insertOrThrow(table,null,v);
    }
    public void delete(String table,long id){if(!Arrays.asList(TABLES).contains(table)||table.equals("settings"))throw new IllegalArgumentException();getWritableDatabase().delete(table,"id=?",new String[]{""+id});}
    public List<Row> grades(long student,long subject,String date){return rows("SELECT * FROM grades WHERE student_id=? AND subject_id=?"+(date==null?"":" AND day=?")+" ORDER BY day DESC,id DESC",date==null?new String[]{""+student,""+subject}:new String[]{""+student,""+subject,date});}
    public long grade(long id,long student,long subject,String day,int value,int maximum,String note){
        LocalDate.parse(day);if(maximum<2||maximum>100||value<1||value>maximum||note.length()>500)throw new IllegalArgumentException();
        ContentValues v=new ContentValues();v.put("student_id",student);v.put("subject_id",subject);v.put("day",day);v.put("value",value);v.put("max_value",maximum);v.put("note",note.trim());
        if(id>0){getWritableDatabase().update("grades",v,"id=?",new String[]{""+id});return id;}return getWritableDatabase().insertOrThrow("grades",null,v);
    }
    public String attendance(long student,long subject,String day){List<Row> r=rows("SELECT status FROM attendance WHERE student_id=? AND subject_id=? AND day=?",""+student,""+subject,day);return r.isEmpty()?"":r.get(0).s("status");}
    public void attendance(long student,long subject,String day,String status){
        LocalDate.parse(day);
        if(status.isEmpty()){getWritableDatabase().delete("attendance","student_id=? AND subject_id=? AND day=?",new String[]{""+student,""+subject,day});return;}
        if(!Arrays.asList("present","absent","late","excused").contains(status))throw new IllegalArgumentException();
        ContentValues v=new ContentValues();v.put("student_id",student);v.put("subject_id",subject);v.put("day",day);v.put("status",status);getWritableDatabase().insertWithOnConflict("attendance",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public JSONObject backup() throws JSONException {
        JSONObject root=new JSONObject();root.put("format","school-journal");root.put("version",1);root.put("exported",LocalDate.now().toString());
        SQLiteDatabase d=getReadableDatabase();d.beginTransaction();
        try{for(String t:TABLES){JSONArray a=new JSONArray();try(Cursor c=d.rawQuery("SELECT * FROM "+t,null)){while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));a.put(o);}}root.put(t,a);}d.setTransactionSuccessful();}finally{d.endTransaction();}return root;
    }
    public void restore(JSONObject root) throws JSONException {
        if(!"school-journal".equals(root.getString("format"))||root.getInt("version")!=1)throw new IllegalArgumentException("format");
        String[][] columns={{"id","name"},{"id","class_id","name"},{"id","class_id","name"},{"id","student_id","subject_id","day","value","max_value","note"},{"id","student_id","subject_id","day","status"},{"key","value"}};
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{
            for(int i=TABLES.length-1;i>=0;i--)d.delete(TABLES[i],null,null);
            for(int i=0;i<TABLES.length;i++){
                JSONArray a=root.getJSONArray(TABLES[i]);if(a.length()>100000)throw new IllegalArgumentException("size");
                for(int j=0;j<a.length();j++){
                    JSONObject o=a.getJSONObject(j);ContentValues v=new ContentValues();
                    for(String col:columns[i]){
                        if(col.equals("id")||col.endsWith("_id")||col.equals("max_value")||col.equals("value")&&!TABLES[i].equals("settings")){long n=o.getLong(col);if(n<1)throw new IllegalArgumentException("id");v.put(col,n);}
                        else{String s=o.getString(col);if(s.length()>500)throw new IllegalArgumentException("length");if(col.equals("day"))LocalDate.parse(s);v.put(col,s);}
                    }
                    if(TABLES[i].equals("settings")){
                        String k=o.getString("key"),val=o.getString("value");
                        if(!Arrays.asList("language","scale","lastClass","lastSubject").contains(k))throw new IllegalArgumentException("setting");
                        if(k.equals("language")&&!Arrays.asList("ru","kk").contains(val))throw new IllegalArgumentException("language");
                        if(k.equals("scale")&&(Integer.parseInt(val)<2||Integer.parseInt(val)>100))throw new IllegalArgumentException("scale");
                        if((k.equals("lastClass")||k.equals("lastSubject"))&&Long.parseLong(val)<0)throw new IllegalArgumentException("selection");
                    }
                    d.insertOrThrow(TABLES[i],null,v);
                }
            }
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
}
