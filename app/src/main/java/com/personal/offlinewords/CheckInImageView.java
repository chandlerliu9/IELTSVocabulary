package com.personal.offlinewords;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.widget.ImageView;

final class CheckInImageView extends ImageView {
    interface ChangeListener { void changed(float scale,float x,float y); }
    private final Matrix matrix=new Matrix();
    private float userScale=1f,offsetX=0,offsetY=0,lastX,lastY,lastDistance;
    private ChangeListener listener;

    CheckInImageView(Context c){super(c);setScaleType(ScaleType.MATRIX);setClickable(true);}
    void setTransform(float scale,float x,float y){userScale=Math.max(.65f,Math.min(4f,scale));offsetX=x;offsetY=y;apply();}
    void setChangeListener(ChangeListener l){listener=l;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);apply();}
    @Override public void setImageDrawable(Drawable d){super.setImageDrawable(d);post(this::apply);}
    private void apply(){Drawable d=getDrawable();if(d==null||getWidth()==0||getHeight()==0)return;float base=Math.max(getWidth()/(float)d.getIntrinsicWidth(),getHeight()/(float)d.getIntrinsicHeight());float total=base*userScale;float x=(getWidth()-d.getIntrinsicWidth()*total)/2f+offsetX;float y=(getHeight()-d.getIntrinsicHeight()*total)/2f+offsetY;matrix.reset();matrix.setScale(total,total);matrix.postTranslate(x,y);setImageMatrix(matrix);invalidate();}
    private float distance(MotionEvent e){float x=e.getX(0)-e.getX(1),y=e.getY(0)-e.getY(1);return (float)Math.sqrt(x*x+y*y);}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();return true;case MotionEvent.ACTION_POINTER_DOWN:if(e.getPointerCount()>=2)lastDistance=distance(e);return true;case MotionEvent.ACTION_MOVE:if(e.getPointerCount()>=2){float now=distance(e);if(lastDistance>0){userScale=Math.max(.65f,Math.min(4f,userScale*now/lastDistance));lastDistance=now;apply();}}else{offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;lastX=e.getX();lastY=e.getY();apply();}return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(listener!=null)listener.changed(userScale,offsetX,offsetY);performClick();return true;}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
