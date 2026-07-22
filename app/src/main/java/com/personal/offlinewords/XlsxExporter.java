package com.personal.offlinewords;

import android.database.Cursor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

final class XlsxExporter {
    private static String xml(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static void entry(ZipOutputStream z,String name,String value)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    static byte[] write(Cursor c)throws IOException{
        String[] headers={"order","term","phonetic_ipa","part_of_speech","definition_zh","definition_en","example_en","example_zh","audio_file","status","favorite"};
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int row=1;sheet.append("<row r=\"1\">");for(int i=0;i<headers.length;i++)cell(sheet,i,row,headers[i]);sheet.append("</row>");
        while(c.moveToNext()){row++;sheet.append("<row r=\"").append(row).append("\">");cell(sheet,0,row,c.getString(0));cell(sheet,1,row,c.getString(1));cell(sheet,2,row,c.getString(2));cell(sheet,3,row,c.getString(3));cell(sheet,4,row,c.getString(4));cell(sheet,5,row,c.getString(5));cell(sheet,6,row,c.getString(6));cell(sheet,7,row,"");cell(sheet,8,row,c.getString(7));cell(sheet,9,row,c.getString(8));cell(sheet,10,row,c.getString(10));sheet.append("</row>");}sheet.append("</sheetData></worksheet>");c.close();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream z=new ZipOutputStream(out);
        entry(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
        entry(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        entry(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"学习状态\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entry(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        entry(z,"xl/worksheets/sheet1.xml",sheet.toString());z.finish();z.close();return out.toByteArray();
    }
    private static void cell(StringBuilder s,int col,int row,String value){s.append("<c r=\"").append(name(col)).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}
    private static String name(int n){StringBuilder s=new StringBuilder();do{s.insert(0,(char)('A'+n%26));n=n/26-1;}while(n>=0);return s.toString();}
}
