package com.redhat.techtalks.camel.business;

import java.io.IOException;
import java.util.Date;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;

public class AgeCalculator {
	private boolean throwException = true;
	
	public int calculateAge(Date dateOfBirth) throws IOException
	{
		if (throwException)
		{
			throwException = false;

			throw new IOException("Exception that can be retried!");
		}

		LocalDate birthDate = LocalDate.fromDateFields(dateOfBirth);
		LocalDate now = new LocalDate();
		Period period = new Period(birthDate, now, PeriodType.yearMonthDay());

		return period.getYears();	
	}
}
