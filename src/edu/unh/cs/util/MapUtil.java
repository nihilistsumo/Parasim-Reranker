package edu.unh.cs.util;

import java.util.*;
import java.util.stream.Collectors;


public class MapUtil {
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
	    return map.entrySet()
	              .stream()
	              .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
	              .collect(Collectors.toMap(
	                Map.Entry::getKey, 
	                Map.Entry::getValue, 
	                (e1, e2) -> e1, 
	                LinkedHashMap::new 
	              ));
	}
	
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map, int topEntryNo) {
		Map<K, V> sorted = MapUtil.sortByValue(map);
		Map<K, V> sortedAndTop = new HashMap<K, V>();
		int count = 0;
		for(Map.Entry<K, V> entry : sorted.entrySet()){
	    	sortedAndTop.put(entry.getKey(), entry.getValue());
	    	count++;
	    	if(count>=topEntryNo)
	    		break;
	    }
		return sortedAndTop;
	}
}
