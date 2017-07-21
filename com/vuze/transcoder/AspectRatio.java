package com.vuze.transcoder;

public class AspectRatio implements Comparable {
	
	int numerator;
	int denominator;
	
	public float getValue() {
		return (float)numerator / (float)denominator;
	}
	
	public int compareTo(Object o) {
		if(o instanceof AspectRatio) {
			AspectRatio other = (AspectRatio) o;
			if (getValue() - other.getValue() == 0) return 0;
			if (getValue() - other.getValue() > 0) return 1;
			return -1;
		}
		return 0;
	}

}
