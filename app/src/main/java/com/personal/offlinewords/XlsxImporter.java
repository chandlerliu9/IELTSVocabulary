package com.personal.offlinewords;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;import java.util.*;import java.util.zip.*;

final class XlsxImporter {
    static final String[] FIELDS={"order","term","phonetic_ipa","part_of_speech","definition_zh","definition_en","example_en","example_zh","audio_file"};
    static List<String[]> read(InputStream source)throws Exception{
        byte[] shared=null,sheet=null;ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source));ZipEntry e;
        while((e=zip.getNextEntry())!=null){String n=e.getName();if(n.equals("xl/sharedStrings.xml"))shared=bytes(zip);else if(n.equals("xl/worksheets/sheet1.xml"))sheet=bytes(zip);zip.closeEntry();}
        if(sheet==null)throw new IOException("Excel 中未找到第一个工作表");List<String> strings=shared==null?Collections.emptyList():sharedStrings(shared);return rows(sheet,strings);
    }
    static byte[] bytes(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    static XmlPullParser parser(byte[] data)throws Exception{XmlPullParser p=Xml.newPullParser();p.setInput(new ByteArrayInputStream(data),"UTF-8");return p;}
    static List<String> sharedStrings(byte[] data)throws Exception{List<String> out=new ArrayList<>();XmlPullParser p=parser(data);StringBuilder current=null;for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){if(ev==XmlPullParser.START_TAG&&p.getName().equals("si"))current=new StringBuilder();else if(ev==XmlPullParser.START_TAG&&p.getName().equals("t")&&current!=null)current.append(p.nextText());else if(ev==XmlPullParser.END_TAG&&p.getName().equals("si")&&current!=null){out.add(current.toString());current=null;}}return out;}
    static List<String[]> rows(byte[] data,List<String> shared)throws Exception{List<Map<Integer,String>> raw=new ArrayList<>();XmlPullParser p=parser(data);Map<Integer,String> row=null;int col=-1;String type="";for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){if(ev==XmlPullParser.START_TAG&&p.getName().equals("row"))row=new HashMap<>();else if(ev==XmlPullParser.START_TAG&&p.getName().equals("c")){col=column(p.getAttributeValue(null,"r"));type=p.getAttributeValue(null,"t");}else if(ev==XmlPullParser.START_TAG&&(p.getName().equals("v")||p.getName().equals("t"))&&row!=null&&col>=0){String v=p.nextText();if("s".equals(type)){int i=Integer.parseInt(v);v=i<shared.size()?shared.get(i):"";}row.put(col,v);}else if(ev==XmlPullParser.END_TAG&&p.getName().equals("row")&&row!=null){raw.add(row);row=null;}}if(raw.isEmpty())throw new IOException("Excel 没有数据");Map<String,Integer> header=new HashMap<>();for(Map.Entry<Integer,String>x:raw.get(0).entrySet())header.put(x.getValue().trim().toLowerCase(Locale.ROOT),x.getKey());if(!header.containsKey("term"))throw new IOException("缺少必需表头 term");List<String[]> out=new ArrayList<>();for(int r=1;r<raw.size();r++){Map<Integer,String> m=raw.get(r);String[] a=new String[FIELDS.length];for(int i=0;i<a.length;i++){Integer c=header.get(FIELDS[i]);a[i]=c==null?"":m.containsKey(c)?m.get(c):"";}if(!a[1].trim().isEmpty()){if(a[0].isEmpty())a[0]=String.valueOf(out.size()+1);out.add(a);}}return out;}
    static int column(String ref){if(ref==null)return -1;int n=0;for(int i=0;i<ref.length()&&Character.isLetter(ref.charAt(i));i++)n=n*26+(Character.toUpperCase(ref.charAt(i))-'A'+1);return n-1;}
}
