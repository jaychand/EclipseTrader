/*******************************************************************************
 * Copyright (c) 2004-2005 Marco Maccaferri and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Marco Maccaferri - initial API and implementation
 *******************************************************************************/
package net.sourceforge.eclipsetrader.yahoo.internal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import net.sourceforge.eclipsetrader.IExtendedData;
import net.sourceforge.eclipsetrader.TraderPlugin;
import net.sourceforge.eclipsetrader.yahoo.IndexDataProvider;
import net.sourceforge.eclipsetrader.yahoo.SnapshotDataProvider;
import net.sourceforge.eclipsetrader.yahoo.YahooPlugin;
import sun.misc.BASE64Encoder;

/**
 */
public class Streamer
{
  private static Streamer instance;
  private Timer timer;
  private SimpleDateFormat dtf = new SimpleDateFormat("MM/dd/yyyy h:mma");
  private SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
  private SimpleDateFormat tf = new SimpleDateFormat("h:mma");
  private HashMap data = new HashMap();
  private List listeners = new ArrayList();
  
  private Streamer()
  {
  }
  
  public static Streamer getInstance()
  {
    if (instance == null)
      instance = new Streamer();
    return instance;
  }
  
  public void start()
  {
    if (timer == null)
    {
      timer = new Timer();
      timer.schedule(new TimerTask() {
        public void run() {
          update();
        }
      }, 2 * 1000);
    }
  }
  
  public void stop()
  {
    if (timer != null)
    {
      timer.cancel();
      timer = null;
    }
  }
  
  public void addSymbol(String symbol)
  {
    if (data.get(symbol) == null)
      data.put(symbol.toUpperCase(), TraderPlugin.createExtendedData());
  }
  
  public void removeSymbol(String symbol)
  {
    data.remove(symbol.toUpperCase());
  }
  
  public IExtendedData getData(String symbol)
  {
    return (IExtendedData)data.get(symbol.toUpperCase());
  }

  private void update()
  {
    // Builds the url for quotes download
    StringBuffer url = new StringBuffer(YahooPlugin.getDefault().getPreferenceStore().getString("yahoo.quote") + "?symbols=");
    Iterator iterator = data.keySet().iterator();
    while(iterator.hasNext() == true)
      url = url.append((String)iterator.next() + "+");
    if (url.charAt(url.length() - 1) == '+')
      url.deleteCharAt(url.length() - 1);
    url.append("&format=sl1d1t1c1ohgvbap");
    
    // Read the last prices
    String line = "";
    try {
      HttpURLConnection con = (HttpURLConnection)new URL(url.toString()).openConnection();
      String proxyHost = (String)System.getProperties().get("http.proxyHost");
      String proxyUser = (String)System.getProperties().get("http.proxyUser");
      String proxyPassword = (String)System.getProperties().get("http.proxyPassword");
      if (proxyHost != null && proxyHost.length() != 0 && proxyUser != null && proxyUser.length() != 0 && proxyPassword != null)
      {
        String login = proxyUser + ":" + proxyPassword;
        String encodedLogin = new BASE64Encoder().encodeBuffer(login.getBytes());
        con.setRequestProperty("Proxy-Authorization", "Basic " + encodedLogin.trim());
      }
      con.setAllowUserInteraction(true);
      con.setRequestMethod("GET");
      con.setInstanceFollowRedirects(true);
      BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
      while ((line = in.readLine()) != null) 
      {
        String[] item;
        if (line.indexOf(";") != -1)
          item = line.split(";");
        else
          item = line.split(",");

        // 0 = Code
        IExtendedData pd = (IExtendedData)data.get(stripQuotes(item[0].toUpperCase()));
        if (pd == null)
          continue;
        
        // 2 = Date
        // 3 = Time
        try {
          GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("EST"), Locale.US);
          df.setTimeZone(c.getTimeZone());
          tf.setTimeZone(c.getTimeZone());
          dtf.setTimeZone(c.getTimeZone());

          String date = stripQuotes(item[2]);
          if (date.indexOf("N/A") != -1)
            date = df.format(Calendar.getInstance().getTime());
          String time = stripQuotes(item[3]);
          if (time.indexOf("N/A") != -1)
            time = tf.format(Calendar.getInstance().getTime());
          c.setTime(dtf.parse(date + " " + time));
          c.setTimeZone(TimeZone.getDefault());
          pd.setDate(c.getTime());
        } catch(Exception x) {
          System.out.println(x.getMessage() + ": " + line);
          continue;
        };

        // 1 = Last price or N/A
        if (item[1].equalsIgnoreCase("N/A") == false)
          pd.setLastPrice(Double.parseDouble(item[1].replace(',', '.')));
        
        // 4 = Change
        // 5 = Open
        if (item[5].equalsIgnoreCase("N/A") == false)
          pd.setOpenPrice(Double.parseDouble(item[5].replace(',', '.')));
        // 6 = Maximum
        if (item[6].equalsIgnoreCase("N/A") == false)
          pd.setHighPrice(Double.parseDouble(item[6].replace(',', '.')));
        // 7 = Minimum
        if (item[7].equalsIgnoreCase("N/A") == false)
          pd.setLowPrice(Double.parseDouble(item[7].replace(',', '.')));
        // 8 = Volume
        if (item[8].equalsIgnoreCase("N/A") == false)
          pd.setVolume(Integer.parseInt(item[8]));
        // 9 = Bid Price
        if (item[9].equalsIgnoreCase("N/A") == false)
          pd.setBidPrice(Double.parseDouble(item[9].replace(',', '.')));
        // 10 = Ask Price
        if (item[10].equalsIgnoreCase("N/A") == false)
          pd.setAskPrice(Double.parseDouble(item[10].replace(',', '.')));
        // 11 = Close Price
        if (item[11].equalsIgnoreCase("N/A") == false)
          pd.setClosePrice(Double.parseDouble(item[11].replace(',', '.')));
      }
      in.close();
    } catch(Exception x) {
      System.out.println(x.getMessage() + ": " + line);
    };
    
    iterator = listeners.iterator();
    while(iterator.hasNext() == true)
    {
      Object obj = iterator.next();
      if (obj instanceof SnapshotDataProvider)
        ((SnapshotDataProvider)obj).update();
      if (obj instanceof IndexDataProvider)
        ((IndexDataProvider)obj).update();
    }

    // Schedule the next update.
    int refresh = YahooPlugin.getDefault().getPreferenceStore().getInt("yahoo.refresh");
    try {
      if (timer != null)
      {
        timer.schedule(new TimerTask() {
          public void run() {
            update();
          }
        }, refresh * 1000);
      }
    } catch(IllegalStateException e) {};
  }
  
  public void addListener(Object obj)
  {
    if (listeners.contains(obj) == false)
      listeners.add(obj);
  }
  
  public void removeListener(Object obj)
  {
    listeners.remove(obj);
  }
  
  private String stripQuotes(String s)
  {
    if (s.startsWith("\""))
      s = s.substring(1);
    if (s.endsWith("\""))
      s = s.substring(0, s.length() - 1);
    return s;
  }
}
