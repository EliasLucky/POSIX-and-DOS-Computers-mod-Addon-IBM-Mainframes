package com.eliaslucky.ibm_mainframe.jcl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One job in the spool queue. Immutable fields describe the job;
 * mutable fields track its lifecycle.
 */
public final class SpoolEntry {
	public enum Status { WAITING, ACTIVE, DONE, FAILED }

	private final String id;			 // short hex, shown in JOBS listing
	private final String jobName;
	private final String jobClass;		 // "A", "B", "C"...
	private final String msgClass;		 // output class for the listing
	private final List<String> deck;	 // the card images, in order
	private final long submittedAt;

	private Status status = Status.WAITING;
	private final List<String> listing = new ArrayList<>();

	public SpoolEntry(String id, String jobName, String jobClass,
					  String msgClass, List<String> deck) {
		this.id = id;
		this.jobName = jobName;
		this.jobClass = jobClass;
		this.msgClass = msgClass;
		this.deck = List.copyOf(deck);
		this.submittedAt = System.currentTimeMillis();
	}

	public String id()			   { return id; }
	public String jobName()		   { return jobName; }
	public String jobClass()	   { return jobClass; }
	public String msgClass()	   { return msgClass; }
	public List<String> deck()	   { return deck; }
	public long submittedAt()	   { return submittedAt; }
	public Status status()		   { return status; }
	public List<String> listing()  { return List.copyOf(listing); }

	public void setStatus(Status s) { this.status = s; }
	public void appendListing(String line) { listing.add(line == null ? "" : line); }
	public void appendListing(List<String> lines) {
		for (String l : lines) listing.add(l == null ? "" : l);
	}
}
