package org.devtcg.five.service;

import java.text.ParseException;

import org.devtcg.five.service.MusicMapping.MetaDataFormat;

import junit.framework.TestCase;

public class MusicMappingTest extends TestCase
{
	public void testMetaDataFormatComplianceSimple()
	  throws ParseException
	{
		final String DATA_COMPLIANCE_SIMPLE = 
		  "CONTENT:31\n" +
		  "N:Alright\n" + 
		  "ARTIST_GUID:2\n" + 
		  "ALBUM_GUID:2\n" + 
		  "LENGTH:248\n" + 
		  "TRACK:11\n" + 
		  "DISCOVERY:1223431944\n" +
		  "SIZE:7035008\n";

		MetaDataFormat fmt = new MetaDataFormat(DATA_COMPLIANCE_SIMPLE);

		assertEquals(fmt.getString("CONTENT"), "31");
		assertEquals(fmt.getString("N"), "Alright");
		assertEquals(fmt.getString("ARTIST_GUID"), "2");
		assertEquals(fmt.getString("ALBUM_GUID"), "2");
		assertEquals(fmt.getString("LENGTH"), "248");
		assertEquals(fmt.getString("TRACK"), "11");
		assertEquals(fmt.getString("DISCOVERY"), "1223431944");
		assertEquals(fmt.getString("SIZE"), "7035008");
	}
}
