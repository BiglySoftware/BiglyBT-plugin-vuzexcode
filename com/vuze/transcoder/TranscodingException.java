package com.vuze.transcoder;

public class TranscodingException extends Exception {
	
	public TranscodingException(Throwable t) {
		super(t);
	}
	
	public TranscodingException(String message) {
		super(message);
	}
	
	public TranscodingException(String message,Throwable t) {
		super(message,t);
	}

}
