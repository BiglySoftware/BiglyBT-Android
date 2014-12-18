package com.vuze.android.remote;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 * an {@link ImageView} supporting asynchronous loading from URL. Additional
 * APIs: {@link #setImageURL(URL)}, {@link #cancelLoading()}.
 * 
 * @author ep@gplushub.com / Eugen Plischke
 * 
 * From http://stackoverflow.com/questions/14332296/how-to-set-image-from-url-using-asynctask/15797963#15797963
 * 
 */
public class UrlImageView extends ImageView {
  private static class UrlLoadingTask extends AsyncTask<URL, Void, Bitmap> {
    private final ImageView updateView;
    private boolean         isCancelled = false;
    private InputStream     urlInputStream;

    private UrlLoadingTask(ImageView updateView) {
      this.updateView = updateView;
    }

    @Override
    protected Bitmap doInBackground(URL... params) {
      try {
        URLConnection con = params[0].openConnection();
        // can use some more params, i.e. caching directory etc
        con.setUseCaches(true);
        this.urlInputStream = con.getInputStream();
        return BitmapFactory.decodeStream(urlInputStream);
      } catch (IOException e) {
        Log.w(UrlImageView.class.getName(), "failed to load image from " + params[0], e);
        return null;
      } finally {
        if (this.urlInputStream != null) {
          try {
            this.urlInputStream.close();
          } catch (IOException e) {
            ; // swallow
          } finally {
            this.urlInputStream = null;
          }
        }
      }
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      if (!this.isCancelled) {
        // hope that call is thread-safe
        this.updateView.setImageBitmap(result);
      }
    }

    /*
     * just remember that we were cancelled, no synchronization necessary
     */
    @Override
    protected void onCancelled() {
      this.isCancelled = true;
      try {
        if (this.urlInputStream != null) {
          try {
            this.urlInputStream.close();
          } catch (IOException e) {
            ;// swallow
          } finally {
            this.urlInputStream = null;
          }
        }
      } finally {
        super.onCancelled();
      }
    }
  }

  /*
   * track loading task to cancel it
   */
  private AsyncTask<URL, Void, Bitmap> currentLoadingTask;
  /*
   * just for sync
   */
  private Object                       loadingMonitor = new Object();

  public UrlImageView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public UrlImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public UrlImageView(Context context) {
    super(context);
  }

  @Override
  public void setImageBitmap(Bitmap bm) {
    cancelLoading();
    super.setImageBitmap(bm);
  }

  @Override
  public void setImageDrawable(Drawable drawable) {
    cancelLoading();
    super.setImageDrawable(drawable);
  }

  @Override
  public void setImageResource(int resId) {
    cancelLoading();
    super.setImageResource(resId);
  }

  @Override
  public void setImageURI(Uri uri) {
    cancelLoading();
    super.setImageURI(uri);
  }

  /**
   * loads image from given url
   * 
   * @param url
   */
  public void setImageURL(URL url) {
    synchronized (loadingMonitor) {
      cancelLoading();
      this.currentLoadingTask = new UrlLoadingTask(this).execute(url);
    }
  }

  /**
   * cancels pending image loading
   */
  public void cancelLoading() {
    synchronized (loadingMonitor) {
      if (this.currentLoadingTask != null) {
        this.currentLoadingTask.cancel(true);
        this.currentLoadingTask = null;
      }
    }
  }
}