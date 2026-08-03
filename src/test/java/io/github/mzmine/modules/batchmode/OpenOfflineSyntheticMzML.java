/*
 * Copyright (c) 2026 Contributors to the open offline fork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.mzmine.modules.batchmode;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Writes the established human-readable synthetic profile as a portable mzML file. */
final class OpenOfflineSyntheticMzML {

  private static final String PROFILE_RESOURCE = "/open_offline/synthetic_lcms_profile.csv";
  private static final double[] MZ_VALUES = {75.0, 150.0, 300.0, 500.0};

  private OpenOfflineSyntheticMzML() {
  }

  static void write(final Path output) throws IOException {
    final List<ProfileScan> profile = loadProfile();
    final StringBuilder xml = new StringBuilder(48_000);
    xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        .append("<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.0\" ")
        .append("id=\"synthetic_open_offline_batch\">\n")
        .append("  <cvList count=\"2\">\n")
        .append("    <cv id=\"MS\" fullName=\"Proteomics Standards Initiative Mass Spectrometry Ontology\" version=\"4.1.30\" URI=\"https://raw.githubusercontent.com/HUPO-PSI/psi-ms-CV/master/psi-ms.obo\"/>\n")
        .append("    <cv id=\"UO\" fullName=\"Unit Ontology\" version=\"09:04:2014\" URI=\"https://raw.githubusercontent.com/bio-ontology-research-group/unit-ontology/master/unit.obo\"/>\n")
        .append("  </cvList>\n")
        .append("  <fileDescription><fileContent>\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n")
        .append("  </fileContent></fileDescription>\n")
        .append("  <softwareList count=\"1\"><software id=\"open_offline_fixture_generator\" version=\"1.0\">\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000799\" name=\"custom unreleased software tool\" value=\"Open Offline Fixture Generator\"/>\n")
        .append("  </software></softwareList>\n")
        .append("  <instrumentConfigurationList count=\"1\"><instrumentConfiguration id=\"IC1\">\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000031\" name=\"instrument model\" value=\"synthetic LC-MS\"/>\n")
        .append("  </instrumentConfiguration></instrumentConfigurationList>\n")
        .append("  <dataProcessingList count=\"1\"><dataProcessing id=\"dp1\">\n")
        .append("    <processingMethod order=\"0\" softwareRef=\"open_offline_fixture_generator\">\n")
        .append("      <cvParam cvRef=\"MS\" accession=\"MS:1000544\" name=\"Conversion to mzML\" value=\"synthetic deterministic fixture\"/>\n")
        .append("    </processingMethod>\n")
        .append("  </dataProcessing></dataProcessingList>\n")
        .append("  <run id=\"synthetic_open_offline_batch\" defaultInstrumentConfigurationRef=\"IC1\">\n")
        .append("    <spectrumList count=\"").append(profile.size())
        .append("\" defaultDataProcessingRef=\"dp1\">\n");

    for (int index = 0; index < profile.size(); index++) {
      appendSpectrum(xml, profile.get(index), index);
    }

    xml.append("    </spectrumList>\n")
        .append("  </run>\n")
        .append("</mzML>\n");
    Files.writeString(output, xml.toString(), UTF_8);
  }

  private static List<ProfileScan> loadProfile() throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(OpenOfflineSyntheticMzML.class.getResourceAsStream(PROFILE_RESOURCE),
            "Missing profile fixture " + PROFILE_RESOURCE), UTF_8))) {
      final List<ProfileScan> rows = reader.lines().skip(1).filter(line -> !line.isBlank())
          .map(ProfileScan::parse).toList();
      if (rows.size() != 15) {
        throw new IOException("Expected 15 profile scans, found " + rows.size());
      }
      return rows;
    }
  }

  private static void appendSpectrum(final StringBuilder xml, final ProfileScan row,
      final int index) {
    final double[] intensities = row.intensities();
    int basePeakIndex = 0;
    double totalIonCurrent = 0.0;
    for (int i = 0; i < intensities.length; i++) {
      totalIonCurrent += intensities[i];
      if (intensities[i] > intensities[basePeakIndex]) {
        basePeakIndex = i;
      }
    }

    final String encodedMzs = encodeDoubles(MZ_VALUES);
    final String encodedIntensities = encodeDoubles(intensities);
    xml.append("      <spectrum index=\"").append(index).append("\" id=\"scan=")
        .append(row.scan()).append("\" defaultArrayLength=\"4\">\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"1\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000130\" name=\"positive scan\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000504\" name=\"base peak m/z\" value=\"")
        .append(MZ_VALUES[basePeakIndex]).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000505\" name=\"base peak intensity\" value=\"")
        .append(intensities[basePeakIndex]).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\"")
        .append(totalIonCurrent).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000528\" name=\"lowest observed m/z\" value=\"75.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000527\" name=\"highest observed m/z\" value=\"500.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("        <scanList count=\"1\"><cvParam cvRef=\"MS\" accession=\"MS:1000795\" name=\"no combination\" value=\"\"/>\n")
        .append("          <scan instrumentConfigurationRef=\"IC1\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\"")
        .append(row.rtMinutes()).append("\" unitCvRef=\"UO\" unitAccession=\"UO:0000031\" unitName=\"minute\"/>\n")
        .append("            <scanWindowList count=\"1\"><scanWindow>\n")
        .append("              <cvParam cvRef=\"MS\" accession=\"MS:1000501\" name=\"scan window lower limit\" value=\"50.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("              <cvParam cvRef=\"MS\" accession=\"MS:1000500\" name=\"scan window upper limit\" value=\"550.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("            </scanWindow></scanWindowList>\n")
        .append("          </scan></scanList>\n")
        .append("        <binaryDataArrayList count=\"2\">\n")
        .append("          <binaryDataArray encodedLength=\"").append(encodedMzs.length())
        .append("\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("            <binary>").append(encodedMzs).append("</binary>\n")
        .append("          </binaryDataArray>\n")
        .append("          <binaryDataArray encodedLength=\"")
        .append(encodedIntensities.length()).append("\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000131\" unitName=\"number of detector counts\"/>\n")
        .append("            <binary>").append(encodedIntensities).append("</binary>\n")
        .append("          </binaryDataArray>\n")
        .append("        </binaryDataArrayList>\n")
        .append("      </spectrum>\n");
  }

  private static String encodeDoubles(final double[] values) {
    final ByteBuffer buffer = ByteBuffer.allocate(values.length * Double.BYTES)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (final double value : values) {
      buffer.putDouble(value);
    }
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  private record ProfileScan(int scan, double rtMinutes, double mz75Intensity,
                             double mz150Intensity, double mz300Intensity,
                             double mz500Intensity) {

    static ProfileScan parse(final String csvLine) {
      final String[] values = csvLine.split(",", -1);
      if (values.length != 6) {
        throw new IllegalArgumentException("Expected six columns: " + csvLine);
      }
      return new ProfileScan(Integer.parseInt(values[0]), Double.parseDouble(values[1]),
          Double.parseDouble(values[2]), Double.parseDouble(values[3]),
          Double.parseDouble(values[4]), Double.parseDouble(values[5]));
    }

    double[] intensities() {
      return new double[]{mz75Intensity, mz150Intensity, mz300Intensity, mz500Intensity};
    }
  }
}
