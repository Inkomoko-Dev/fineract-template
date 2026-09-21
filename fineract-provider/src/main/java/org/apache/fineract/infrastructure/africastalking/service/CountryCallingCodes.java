/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.africastalking.service;

import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * ISO 3166-1 alpha-2 to ITU-T E.164 country calling codes.
 */
public final class CountryCallingCodes {

    private static final Map<String, String> DIAL_BY_ISO = Map.ofEntries(entry("AF", "93"), entry("AX", "358"), entry("AL", "355"),
            entry("DZ", "213"), entry("AS", "1"), entry("AD", "376"), entry("AO", "244"), entry("AI", "1"), entry("AG", "1"),
            entry("AR", "54"), entry("AM", "374"), entry("AW", "297"), entry("AU", "61"), entry("AT", "43"), entry("AZ", "994"),
            entry("BS", "1"), entry("BH", "973"), entry("BD", "880"), entry("BB", "1"), entry("BY", "375"), entry("BE", "32"),
            entry("BZ", "501"), entry("BJ", "229"), entry("BM", "1"), entry("BT", "975"), entry("BO", "591"), entry("BA", "387"),
            entry("BW", "267"), entry("BR", "55"), entry("IO", "246"), entry("VG", "1"), entry("BN", "673"), entry("BG", "359"),
            entry("BF", "226"), entry("BI", "257"), entry("KH", "855"), entry("CM", "237"), entry("CA", "1"), entry("CV", "238"),
            entry("KY", "1"), entry("CF", "236"), entry("TD", "235"), entry("CL", "56"), entry("CN", "86"), entry("CX", "61"),
            entry("CC", "61"), entry("CO", "57"), entry("KM", "269"), entry("CG", "242"), entry("CD", "243"), entry("CK", "682"),
            entry("CR", "506"), entry("CI", "225"), entry("HR", "385"), entry("CU", "53"), entry("CW", "599"), entry("CY", "357"),
            entry("CZ", "420"), entry("DK", "45"), entry("DJ", "253"), entry("DM", "1"), entry("DO", "1"), entry("EC", "593"),
            entry("EG", "20"), entry("SV", "503"), entry("GQ", "240"), entry("ER", "291"), entry("EE", "372"), entry("SZ", "268"),
            entry("ET", "251"), entry("FK", "500"), entry("FO", "298"), entry("FJ", "679"), entry("FI", "358"), entry("FR", "33"),
            entry("GF", "594"), entry("PF", "689"), entry("GA", "241"), entry("GM", "220"), entry("GE", "995"), entry("DE", "49"),
            entry("GH", "233"), entry("GI", "350"), entry("GR", "30"), entry("GL", "299"), entry("GD", "1"), entry("GP", "590"),
            entry("GU", "1"), entry("GT", "502"), entry("GG", "44"), entry("GN", "224"), entry("GW", "245"), entry("GY", "592"),
            entry("HT", "509"), entry("HN", "504"), entry("HK", "852"), entry("HU", "36"), entry("IS", "354"), entry("IN", "91"),
            entry("ID", "62"), entry("IR", "98"), entry("IQ", "964"), entry("IE", "353"), entry("IM", "44"), entry("IL", "972"),
            entry("IT", "39"), entry("JM", "1"), entry("JP", "81"), entry("JE", "44"), entry("JO", "962"), entry("KZ", "7"),
            entry("KE", "254"), entry("KI", "686"), entry("XK", "383"), entry("KW", "965"), entry("KG", "996"), entry("LA", "856"),
            entry("LV", "371"), entry("LB", "961"), entry("LS", "266"), entry("LR", "231"), entry("LY", "218"), entry("LI", "423"),
            entry("LT", "370"), entry("LU", "352"), entry("MO", "853"), entry("MG", "261"), entry("MW", "265"), entry("MY", "60"),
            entry("MV", "960"), entry("ML", "223"), entry("MT", "356"), entry("MH", "692"), entry("MQ", "596"), entry("MR", "222"),
            entry("MU", "230"), entry("YT", "262"), entry("MX", "52"), entry("FM", "691"), entry("MD", "373"), entry("MC", "377"),
            entry("MN", "976"), entry("ME", "382"), entry("MS", "1"), entry("MA", "212"), entry("MZ", "258"), entry("MM", "95"),
            entry("NA", "264"), entry("NR", "674"), entry("NP", "977"), entry("NL", "31"), entry("NC", "687"), entry("NZ", "64"),
            entry("NI", "505"), entry("NE", "227"), entry("NG", "234"), entry("NU", "683"), entry("NF", "672"), entry("KP", "850"),
            entry("MK", "389"), entry("MP", "1"), entry("NO", "47"), entry("OM", "968"), entry("PK", "92"), entry("PW", "680"),
            entry("PS", "970"), entry("PA", "507"), entry("PG", "675"), entry("PY", "595"), entry("PE", "51"), entry("PH", "63"),
            entry("PL", "48"), entry("PT", "351"), entry("PR", "1"), entry("QA", "974"), entry("RE", "262"), entry("RO", "40"),
            entry("RU", "7"), entry("RW", "250"), entry("BL", "590"), entry("SH", "290"), entry("KN", "1"), entry("LC", "1"),
            entry("MF", "590"), entry("PM", "508"), entry("VC", "1"), entry("WS", "685"), entry("SM", "378"), entry("ST", "239"),
            entry("SA", "966"), entry("SN", "221"), entry("RS", "381"), entry("SC", "248"), entry("SL", "232"), entry("SG", "65"),
            entry("SX", "1"), entry("SK", "421"), entry("SI", "386"), entry("SB", "677"), entry("SO", "252"), entry("ZA", "27"),
            entry("KR", "82"), entry("SS", "211"), entry("ES", "34"), entry("LK", "94"), entry("SD", "249"), entry("SR", "597"),
            entry("SE", "46"), entry("CH", "41"), entry("SY", "963"), entry("TW", "886"), entry("TJ", "992"), entry("TZ", "255"),
            entry("TH", "66"), entry("TL", "670"), entry("TG", "228"), entry("TK", "690"), entry("TO", "676"), entry("TT", "1"),
            entry("TN", "216"), entry("TR", "90"), entry("TM", "993"), entry("TC", "1"), entry("TV", "688"), entry("UG", "256"),
            entry("UA", "380"), entry("AE", "971"), entry("GB", "44"), entry("US", "1"), entry("UY", "598"), entry("VI", "1"),
            entry("UZ", "998"), entry("VU", "678"), entry("VA", "39"), entry("VE", "58"), entry("VN", "84"), entry("WF", "681"),
            entry("EH", "212"), entry("YE", "967"), entry("ZM", "260"), entry("ZW", "263"));

    private CountryCallingCodes() {}

    public static String dialCodeFor(final String countryCode) {
        if (StringUtils.isBlank(countryCode)) {
            return null;
        }
        final String trimmed = countryCode.trim();
        final String iso = DIAL_BY_ISO.get(trimmed.toUpperCase(Locale.ROOT));
        if (iso != null) {
            return iso;
        }
        return StringUtils.getDigits(trimmed);
    }

    private static Map.Entry<String, String> entry(final String iso, final String dial) {
        return Map.entry(iso, dial);
    }
}
