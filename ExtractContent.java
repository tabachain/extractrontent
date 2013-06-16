package com.clipitfree;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Build;

import com.evernote.edam.notestore.NoteList;

public class ExtractContent {
	private static int threshold = 100;
	private static int min_length = 80;
	private static double decay_factor = 0.73;
	private static double continuous_factor = 1.62;
	private static int punctuation_weight = 10;
	private static String punctuations = "([、。，．！？]|\\.[^A-Za-z0-9]|,[^0-9]|!|\\?)";
	private static String waste_expressions = "(?i)Copyright|All Rights Reserved";
	private static String dom_separator = "";
	private static boolean debug = false;
	//実体参照変換
	private static final Map<String, String> CHARREF = 
		    new HashMap<String, String>() {
		  {
		    put("&nbsp;" , " ");
		    put("&nbsp;" , " ");
		    put( "&gt;"  , ">");
		    put("&amp;"  , "&");
		    put("&laquo;", "0xC2 0xAB");
		    put( "&raquo;", "0xC2 0xBB");
		    
		  }
		};
	
	public static String analyse(String html){
		String title = extract_title(html);
	      String maxkey = "";
	      int max = 0;
//		html = html.replaceAll(	"<!--\\s*google_ad_section_start\\(weight=ignore\\)\\s*-->.*?<!--\\s*google_ad_section_end.*?-->", "");
//	    Matcher m = Pattern.compile("<!--\\s*google_ad_section_start[^>]*-->").matcher(html);
//		if(m.find()){
//			Matcher m2 = Pattern.compile("(?s)<!--\\s*google_ad_section_start[^>]*-->.*?<!--\\s*google_ad_section_end.*?-->").matcher(html);
//			String tmp = "";
//			while(m2.find()){
//				tmp = tmp + m2.group(0) + "\n";
//			}
//			html = tmp;
//		} 
	      html = eliminate_useless_tags(html);
			double factor = 1.0;
			double continuous = 1.0;
			String body = "";
			int score = 0;
			Map<String,Integer> bodylist = new HashMap<String, Integer>();
			//String[] list = html.split("<\\/?(?:div|center|td)[^>]*>|<p\\s*[^>]*class\\s*=\\s*[\"']?(?:posted|plugin-\\w+)['\"]?[^>]*>");
			String[] list = html.split("(?s)<\\/?(?:div|center|td)[^>]*>|<p\\s*[^>]*class\\s*=\\s*[\"']?(?:posted|plugin-\\w+)['\"]?[^>]*>");
			for(String block: list){
	        	if(block == "")continue;
	        	block = strip(block, null);
	        	if(has_only_tags(block))continue;
	        	if(body.length() > 0)continuous = continuous /continuous_factor;
	        	//リンク除外＆リンクリスト判定
	            String notelinked = eliminate_link(block);
	             if( notelinked.length() < min_length)continue;
	            //スコア算出
	            double c = (notelinked.length() + scan(notelinked, punctuations).length * punctuation_weight) * factor;
	            factor *= decay_factor;
	            double not_body_rate = scan(block,waste_expressions).length + scan(block,"(?i)amazon[a-z0-9\\.\\/\\-\\?&]+-22").length / 2.0;
	           if (not_body_rate>0)    c *= (Math.pow(0.72, not_body_rate)) ;
	            double c1 = c * continuous;
	           if(c1 > threshold){
	        	   body += block + "\n";
	        	   score += c1;
	        	   continuous = continuous_factor;
	        	   
	           }else if(c > threshold){
	        	   bodylist.put(body, score);
	        	   body = block + "\n";
	        	   score = (int)c;
	        	   continuous = continuous_factor;
	           }
	           
	        
	        }
	       
	  
	        bodylist.put(body, score);
	        for (Iterator it = bodylist.entrySet().iterator(); it.hasNext();) {
	            Map.Entry entry = (Map.Entry)it.next();
	            String key = (String)entry.getKey();
	            int value = (Integer)entry.getValue();
	            if(value >= max){
	            	max = value;
	            	maxkey = key;
	            	
	            }
	        }
	     	
		
		 return maxkey;
	}
	
	private static String[] scan(String str,String pattern){
		List<String>  string_list = new ArrayList<String>();
		Matcher m = Pattern.compile(pattern).matcher(str);
		int size = 0;
		while(m.find()){
			size ++;
			string_list.add(m.group(0));
		}
		String[] string_array = new String[size];
		return string_list.toArray(string_array);
	}
	private static String extract_title(String st){
		Matcher m = Pattern.compile("(?i)<title[^>]*>\\s*(.*?)\\s*<\\/title\\s*>").matcher(st);
		if(m.find()){
			return strip_tags(m.group(1));
		}else{
			return "";
		}
	}
	
	private static boolean has_only_tags(String st){
		boolean bool = strip(st.replaceAll("(?i)(?s)<[^>]*>", "").replaceAll("&nbsp;",""),null).length() == 0;
	    return bool;
	}
	private static String eliminate_useless_tags(String html){
		 //eliminate useless html tags
		//html = html.replaceAll("<(script|style|select|noscript)[^>]*>.*?<\\/\\1\\s*>", "");
		//html = html.replaceAll("(?s)<!--.*?-->", "");
		//html = html.replaceAll("<![A-Za-z].*?>","");
		//html = html.replaceAll("<div\\s[^>]*class\\s*=\\s*['\"]?alpslab-slide[\"']?[^>]*>.*?<\\/div\\s*>", "");
		//html = html.replaceAll("<div\\s[^>]*(id|class)\\s*=\\s*['\"]?\\S*more\\S*[\"']?[^>]*>","");
		ArrayList<String> removeStrings = new ArrayList<String>();
		removeStrings.add("<!--(.|\n)*?-->");
		removeStrings.add("(?i)(?s)<script[^>]*?>[\\s\\S]*?<\\/script>");
		removeStrings.add("(?i)(?s)<select[^>]*?>[\\s\\S]*?<\\/select>");
		removeStrings.add("(?i)(?s)<noscript[^>]*?>[\\s\\S]*?<\\/noscript>");
		removeStrings.add("(?i)(?s)<style[^>]*?>[\\s\\S]*?<\\/style>");
		removeStrings.add("(?s)<!--.*?-->");
		for (String removeString : removeStrings) {
			Pattern p = Pattern.compile(removeString, Pattern.CASE_INSENSITIVE);
			html = p.matcher(html).replaceAll("");

		}
		return html;
	}
	
	private static String eliminate_link(String html){
		int count = 0;
		StringBuffer sb = new StringBuffer();
		Matcher m = Pattern.compile("(?i)(?s)<a\\s[^>]*>.*?<\\/a\\s*>").matcher(html);
		while(m.find()){
			count ++;
			m.appendReplacement(sb, "");
			//m.appendReplacement(sb, m.group(0));
		}
		m.appendTail(sb);
		html = sb.toString();
		html = html.replaceAll("(?i)(?s)<form\\s[^>]*>.*?<\\/form\\s*>", "");
		html = strip_tags(html);
		boolean islinklist = islinklist(html);
		int length = html.length();
		if(length < 20 * count ||islinklist){
			return "";
		}else{
			return html;
		}
		
		
	}
	private static boolean islinklist(String st){
		Matcher m = Pattern.compile("(?s)(?i)<(?:ul|dl|ol)(.+?)<\\/(?:ul|dl|ol)>").matcher(st);
		String listpart = "";
		if(m.find()){
			listpart = m.group(1);
			String outside = "";
			
				outside = st.replaceAll("(?i)(?s)<(?:ul|dl)(.+?)<\\/(?:ul|dl)>", "").replaceAll("(?s)<.+?>", "").replaceAll("//s+", " ");
				
			
			String[] list = listpart.split("<li[^>]*>");
			 System.arraycopy(list, 1, list, 0, list.length - 1);
		    int rate = evaluate_list(list);
				return       outside.length() <= st.length() / (45 / rate);
		}
		return false;
		
	}
	
	private static int evaluate_list(String[] list_array){
		if(list_array.length == 0)return 1;
		int hit = 0;
		for(String list : list_array){
			if(list.matches("(?i)(?s)<a\\s+href=(['\"]?)([^\"'\\s]+)\\1"))hit +=1 ;
		}
		 return 9 * (1 * hit / list_array.length) * 9 * (1 * hit / list_array.length) + 1;
	}
	
	private static String strip_tags(String html){
		String no_tag = html.replaceAll("(?s)<.+?>", "");
		return no_tag;
	}
	public static String strip(String str, String stripChars) {
        
        str = stripStart(str, stripChars);
        return stripEnd(str, stripChars);
    }


    public static String stripStart(String str, String stripChars) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }
        int start = 0;
        if (stripChars == null) {
            while ((start != strLen) && Character.isWhitespace(str.charAt(start))) {
                start++;
            }
        } else if (stripChars.length() == 0) {
            return str;
        } else {
            while ((start != strLen) && (stripChars.indexOf(str.charAt(start)) != -1)) {
                start++;
            }
        }
        return str.substring(start);
    }


    public static String stripEnd(String str, String stripChars) {
        int end;
        if (str == null || (end = str.length()) == 0) {
            return str;
        }

        if (stripChars == null) {
            while ((end != 0) && Character.isWhitespace(str.charAt(end - 1))) {
                end--;
            }
        } else if (stripChars.length() == 0) {
            return str;
        } else {
            while ((end != 0) && (stripChars.indexOf(str.charAt(end - 1)) != -1)) {
                end--;
            }
        }
        return str.substring(0, end);
    }
    
    public static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }   
}
