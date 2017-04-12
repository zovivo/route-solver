/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.giggsoff.jspritproj.models;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.giggsoff.jspritproj.utils.Reader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author giggsoff
 */
public class SGB {
    public String id;
    public Point coord;
    public int state;
    public Date cleaning_time;
    public SGB(JSONObject obj) throws JSONException, ParseException, IOException{
        id = obj.getString("id");
        coord = Reader.readGeoJSONPoint(obj.getString("geometry"));
        state = Integer.parseInt(obj.getString("state"));
        //SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //cleaning_time = dateFormat.parse(obj.getString("cleaning_time"));
    }
    
    public static List<SGB> fromArray(JSONArray ar) throws JSONException, ParseException, IOException{
        List<SGB> temp = new ArrayList<>();
        for(int i=0;i<ar.length();i++){
            temp.add(new SGB(ar.getJSONObject(i)));
        }
        return temp;
    }
}
