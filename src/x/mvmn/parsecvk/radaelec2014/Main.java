package x.mvmn.parsecvk.radaelec2014;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.EditorKit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.OfficeDrawing;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.microsoft.POIFSContainerDetector;
import org.freehep.graphicsio.emf.EMFInputStream;
import org.freehep.graphicsio.emf.EMFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Main {

	private static final String CACHE_PATH = "./results/httpcache/";
	private static final String CONTENT_PATH = "./results/";
	private static final String ATTACHMENTS_PATH = "./results/attach/";
	private static final String BASE_URL = "http://www.cvk.gov.ua/pls/vnd2014/";
	private static final String DISTRICT_DATA_URL_PATTERN = "WP033?PT001F01=910&pf7331=%districtNum";

	private static final Map<String, String> monthsNamesToNumbers;
	static {
		monthsNamesToNumbers = new HashMap<String, String>();
		monthsNamesToNumbers.put("сiчня", "01");
		monthsNamesToNumbers.put("січня", "01");
		monthsNamesToNumbers.put("лютого", "02");
		monthsNamesToNumbers.put("березня", "03");
		monthsNamesToNumbers.put("квiтня", "04");
		monthsNamesToNumbers.put("квітня", "04");
		monthsNamesToNumbers.put("травня", "05");
		monthsNamesToNumbers.put("червня", "06");
		monthsNamesToNumbers.put("липня", "07");
		monthsNamesToNumbers.put("серпня", "08");
		monthsNamesToNumbers.put("вересня", "09");
		monthsNamesToNumbers.put("жовтня", "10");
		monthsNamesToNumbers.put("листопада", "11");
		monthsNamesToNumbers.put("грудня", "12");
	}

	private static final String[] TOTALS_TABLE_ROW_LABELS = { "Кількість зареєстрованих кандидатів у депутати",
			"Кількість кандидатів у депутати, реєстрацію яких скасовано до дня виборів",
			"Кількість кандидатів у депутати, які беруть участь у балотуванні, у тому числі висунутих", "партіями", "шляхом самовисування" };

	public static class DistrictInfo {
		public int number;
		public String title;
		public String region;
		public String center;
		public String range;

		public int totalCandidatesRegistered;
		public int totalCandidatesCancelled;
		public int totalCandidatesInElections;
		public int partyCandidatesInElections;
		public int selfproposedCandidatesInElections;

		@Override
		public String toString() {
			return "DistrictInfo [number=" + number + ", title=" + title + ", region=" + region + ", center=" + center + ", range=" + range
					+ ", totalCandidatesRegistered=" + totalCandidatesRegistered + ", totalCandidatesCancelled=" + totalCandidatesCancelled
					+ ", totalCandidatesInElections=" + totalCandidatesInElections + ", partyCandidatesInElections=" + partyCandidatesInElections
					+ ", selfproposedCandidatesInElections=" + selfproposedCandidatesInElections + "]";
		}
	}

	public static class CandidateInfo {
		public String fullName;
		public String programLink;
		public String programFile;
		public String programText;
		public List<String> programImageFiles = new ArrayList<String>();
		public String partyListElection;
		public Date registrationDate;
		public Date cancellationDate;
		public String cancellationReason;
		public boolean cancelled = false;
		public Date dateOfBirth;
		public String placeOfBirth;
		public String citizenship;
		public String livesInCountry;
		public String education;
		public String occupation;
		public String partyMembership;
		public String address;
		public String criminalRecord;

		@Override
		public String toString() {
			return "CandidateInfo [fullName=" + fullName + ", programLink=" + programLink + ", programFile=" + programFile + ", programText=" + programText
					+ ", programImageFiles=" + programImageFiles + ", partyListElection=" + partyListElection + ", registrationDate=" + registrationDate
					+ ", cancellationDate=" + cancellationDate + ", cancellationReason=" + cancellationReason + ", cancelled=" + cancelled + ", dateOfBirth="
					+ dateOfBirth + ", placeOfBirth=" + placeOfBirth + ", education=" + education + ", occupation=" + occupation + ", partyMembership="
					+ partyMembership + ", address=" + address + ", criminalRecord=" + criminalRecord + "]";
		}
	}

	public static class DistrictPageInfo {
		public Date lastUpdateDate;
		public DistrictInfo districtInfo;
		public List<CandidateInfo> candidatesInfo = new ArrayList<CandidateInfo>();

		@Override
		public String toString() {
			final StringBuilder result = new StringBuilder("DistrictPageInfo [lastUpdateDate=" + lastUpdateDate + ", districtInfo=" + districtInfo
					+ ", candidatesInfo=");
			if (candidatesInfo != null) {
				result.append("\n[");
				for (CandidateInfo candidateInfo : candidatesInfo) {
					result.append("\n").append(candidateInfo);
				}
				result.append("\n]");
			} else {
				result.append("null");
			}
			result.append("]");
			return result.toString();
		}
	}

	public static void main(String args[]) throws Exception {
		final SimpleDateFormat commonDateFormat = new SimpleDateFormat("dd.MM.yyyy");
		final SimpleDateFormat lastUpdateDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

		final Gson gson = new GsonBuilder().setPrettyPrinting().create();

		final List<Detector> detectors = new ArrayList<Detector>();
		detectors.add(new POIFSContainerDetector());
		detectors.add(MimeTypes.getDefaultMimeTypes());

		final Detector tikaDetector = new CompositeDetector(detectors);

		for (int districtNum = 1; districtNum < 226; districtNum++) {
			System.out.print("District " + districtNum + " data: Loading...");
			final Document page = get(districtNum);
			System.out.print(" Parsing...");
			if (page.select("#content table:eq(1) td").size() > 0) {
				final String lastUpdateDateStr = page.select("#content table:eq(1) td").first().ownText();
				final Date lastUpdateDate = lastUpdateDateFormat.parse(lastUpdateDateStr.substring(lastUpdateDateStr.indexOf(": ") + 2));
				final DistrictPageInfo pageInfo = new DistrictPageInfo();
				pageInfo.districtInfo = new DistrictInfo();
				pageInfo.districtInfo.number = districtNum;
				pageInfo.lastUpdateDate = lastUpdateDate;

				{
					final List<String> headingLines = extractNodesText(page.select("#content p").first().childNodes());
					if (headingLines.size() != 4) {
						throw new RuntimeException("Heading parsing failure for #" + districtNum + ": expected 4 lines but got " + headingLines.size());
					}
					pageInfo.districtInfo.title = headingLines.get(0);
					pageInfo.districtInfo.region = headingLines.get(1);
					pageInfo.districtInfo.center = headingLines.get(2);
					pageInfo.districtInfo.range = headingLines.get(3);
				}

				{
					final Element totalsTable = page.select("#content table.t2").first();
					Map<String, String> totals = new HashMap<String, String>();
					for (final String rowLabel : TOTALS_TABLE_ROW_LABELS) {
						totals.put(rowLabel, totalsTable.select("tr:contains(" + rowLabel + ") td.td2").first().text());
					}
					pageInfo.districtInfo.totalCandidatesRegistered = Integer.parseInt(totals.get(TOTALS_TABLE_ROW_LABELS[0]).trim());
					pageInfo.districtInfo.totalCandidatesCancelled = Integer.parseInt(totals.get(TOTALS_TABLE_ROW_LABELS[1]).trim());
					pageInfo.districtInfo.totalCandidatesInElections = Integer.parseInt(totals.get(TOTALS_TABLE_ROW_LABELS[2]).trim());
					pageInfo.districtInfo.partyCandidatesInElections = Integer.parseInt(totals.get(TOTALS_TABLE_ROW_LABELS[3]).trim());
					pageInfo.districtInfo.selfproposedCandidatesInElections = Integer.parseInt(totals.get(TOTALS_TABLE_ROW_LABELS[4]).trim());
				}

				final Elements candidatesInfoRows = page.select("#content table.t2").last().select("tr:gt(0)");
				for (Element candidateInfoRow : candidatesInfoRows) {
					final String name = candidateInfoRow.select("td").first().text().trim();
					final Element linkToProgramElement = candidateInfoRow.select("td:eq(3) a").first();

					final CandidateInfo candidateInfo = new CandidateInfo();
					pageInfo.candidatesInfo.add(candidateInfo);
					candidateInfo.fullName = name;

					candidateInfo.registrationDate = commonDateFormat.parse(candidateInfoRow.select("td:eq(4)").text().trim());
					final List<String> regCancellationInfo = extractNodesText(candidateInfoRow.select("td:eq(5)").first().childNodes());
					if (regCancellationInfo.size() > 1) {
						if (regCancellationInfo.size() != 2) {
							throw new RuntimeException("Unexpected registration cancellation info content for #" + districtNum + ": expected 2 lines but got "
									+ regCancellationInfo.size());
						}
						candidateInfo.cancellationDate = commonDateFormat.parse(regCancellationInfo.get(0).trim());
						candidateInfo.cancellationReason = regCancellationInfo.get(1).trim();
						candidateInfo.cancelled = true;
					}

					candidateInfo.partyListElection = candidateInfoRow.select("td:eq(2)").first().text().trim();
					final String candidateInfoTextStr = candidateInfoRow.select("td:eq(1)").first().text().trim();
					final String candidateInfoText[] = candidateInfoTextStr.replaceAll("[\\p{Z}\\s]+", " ").split("\\s*,\\s*");
					if (candidateInfoText.length < 9) {
						throw new RuntimeException("Failed to parse " + districtNum + " " + name + " candidate info - unexpected amount of items: "
								+ candidateInfoText.length + " == " + candidateInfoTextStr);
					}
					for (final Map.Entry<String, String> monthName : monthsNamesToNumbers.entrySet()) {
						candidateInfoText[0] = candidateInfoText[0].replaceAll(" " + monthName.getKey() + " ", "." + monthName.getValue() + ".");
					}
					Matcher matcher = Pattern.compile("(.*)(\\s+\\d+\\.\\d+\\.\\d+\\s+)року\\s+(.*)").matcher(candidateInfoText[0]);
					if (!matcher.find()) {
						throw new RuntimeException("Failed to find " + districtNum + " " + name + " date of birth in date+place of birth: "
								+ candidateInfoText[0]);
					}
					candidateInfo.dateOfBirth = commonDateFormat.parse(matcher.group(2).trim());
					candidateInfoText[0] = matcher.replaceFirst("$1 $3");
					int citizenshipOffset = 0;
					for (int idx = 0; idx < candidateInfoText.length; idx++) {
						if (candidateInfoText[idx].startsWith("громадя")) {
							citizenshipOffset = idx;
							break;
						}
					}
					if (citizenshipOffset == 0) {
						throw new RuntimeException("Wrong  data for " + districtNum + " " + name + " - can't find citizenship: " + candidateInfoTextStr);
					}
					candidateInfo.placeOfBirth = candidateInfoText[0];
					candidateInfo.citizenship = candidateInfoText[citizenshipOffset];
					candidateInfo.livesInCountry = candidateInfoText[citizenshipOffset + 1];
					candidateInfo.education = candidateInfoText[citizenshipOffset + 2];
					if (!candidateInfo.education.startsWith("освіта")) {
						throw new RuntimeException("Wrong education data for " + districtNum + " " + name + ": " + candidateInfo.education + " == "
								+ candidateInfoTextStr);
					}
					int offset = candidateInfoText.length - 9;
					candidateInfo.occupation = "";
					for (int off = 0; off <= offset; off++) {
						candidateInfo.occupation += candidateInfoText[4 + off] + " ";
					}
					candidateInfo.occupation = candidateInfo.occupation.trim();
					candidateInfo.partyMembership = candidateInfoText[5 + offset];
					candidateInfo.address = candidateInfoText[6 + offset];
					if (!candidateInfo.address.startsWith("прожив")) {
						throw new RuntimeException("Wrong address data for " + districtNum + " " + name + ": " + candidateInfo.address + " == "
								+ candidateInfoTextStr);
					}
					candidateInfo.criminalRecord = candidateInfoText[7 + offset];

					if (linkToProgramElement != null) {
						final String href = linkToProgramElement.attr("href");
						candidateInfo.programLink = BASE_URL + href;

						final byte[] programFileContent = IOUtils.toByteArray(getBinaryWithCaching(BASE_URL + href));
						final MediaType mediaType = tikaDetector.detect(new BufferedInputStream(new ByteArrayInputStream(programFileContent)), new Metadata());

						String programFileExtension = mediaType.getSubtype();
						if (programFileExtension.equalsIgnoreCase("x-tika-msoffice")) {
							programFileExtension = "doc";
						} else if (programFileExtension.equalsIgnoreCase("x-tika-ooxml")) {
							programFileExtension = "docx";
						} else if (programFileExtension.equalsIgnoreCase("vnd.ms-excel")) {
							programFileExtension = "xls";
						}
						candidateInfo.programFile = "program_" + districtNum + "_" + name.replace("\\s+", "_") + "." + programFileExtension;
						FileUtils.writeByteArrayToFile(new File(ATTACHMENTS_PATH, candidateInfo.programFile), programFileContent);

						String text = null;
						final List<byte[]> documentImages = new ArrayList<byte[]>();
						if (mediaType.getType().equalsIgnoreCase("image")) {
							documentImages.add(programFileContent);
						} else {
							try {
								final XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(programFileContent));
								final XWPFWordExtractor docExtractor = new XWPFWordExtractor(doc);
								text = docExtractor.getText();

								for (XWPFPictureData pic : doc.getAllPackagePictures()) {
									documentImages.add(pic.getData());
								}

								docExtractor.close();
							} catch (Exception e) {
								try {
									final POIFSFileSystem poiFileSystem = new POIFSFileSystem(new ByteArrayInputStream(programFileContent));
									final HWPFDocument wordDoc = new HWPFDocument(poiFileSystem);
									text = wordDoc.getDocumentText();
									for (final OfficeDrawing drawing : wordDoc.getOfficeDrawingsMain().getOfficeDrawings()) {
										byte[] picData = drawing.getPictureData();
										if (picData != null) {
											documentImages.add(picData);
										}
									}
									for (final OfficeDrawing drawing : wordDoc.getOfficeDrawingsHeaders().getOfficeDrawings()) {
										byte[] picData = drawing.getPictureData();
										if (picData != null) {
											documentImages.add(picData);
										}
									}
									for (final Picture picture : wordDoc.getPicturesTable().getAllPictures()) {
										documentImages.add(picture.getContent());
									}
								} catch (Exception ex) {
									try {
										// RTF
										final JEditorPane p = new JEditorPane();
										p.setContentType("text/rtf");
										final EditorKit rtfKit = p.getEditorKitForContentType("text/rtf");
										rtfKit.read(new ByteArrayInputStream(programFileContent), p.getDocument(), 0);

										// convert to text
										EditorKit txtKit = p.getEditorKitForContentType("text/plain");
										Writer writer = new StringWriter();
										txtKit.write(writer, p.getDocument(), 0, p.getDocument().getLength());
										String documentText = writer.toString();
										if (documentText.contains("à")) {
											documentText = new String(documentText.getBytes("ISO8859_1"), "Cp1251");
										}
										text = documentText;
									} catch (Exception exx) {
										System.err.println("Unknown format: " + urlToFileName(BASE_URL + href));
									}
								}
							}
						}
						if (text != null) {
							if (!text.trim().isEmpty() && !(text.trim().length() < 5)) {
								candidateInfo.programText = text.trim();
								final String cacheFileName = "district_" + districtNum + "_program_" + name + ".txt";
								FileUtils.write(new File(ATTACHMENTS_PATH, cacheFileName), text.trim(), "UTF-8");
								if (!text.toLowerCase().contains("програм") && !text.toLowerCase().contains("украї")) {
									System.err.println("Warning - supposedly bad encoding: " + cacheFileName);
								}
							} else if (documentImages.size() < 1) {
								System.err.println("Empty (or very small) text and no images for " + districtNum + " " + name + ": " + text.trim());
							}
							int imgIdx = 0;
							for (byte[] imageData : documentImages) {
								final MediaType imageMediaType = tikaDetector.detect(new ByteArrayInputStream(imageData), new Metadata());

								String extension = imageMediaType.getSubtype();
								if (extension.equalsIgnoreCase("x-emf") || extension.equalsIgnoreCase("emf") || extension.equalsIgnoreCase("wmf")
										|| extension.equalsIgnoreCase("x-wmf")) {
									// Try to convert EMF to JPEG
									EMFInputStream emfInputStream = new EMFInputStream(new ByteArrayInputStream(imageData), EMFInputStream.DEFAULT_VERSION);
									EMFRenderer emfRenderer = new EMFRenderer(emfInputStream);

									final int width = (int) emfInputStream.readHeader().getBounds().getWidth();
									final int height = (int) emfInputStream.readHeader().getBounds().getHeight();
									final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
									final Graphics2D g2 = (Graphics2D) result.createGraphics();
									emfRenderer.paint(g2);
									ByteArrayOutputStream baos = new ByteArrayOutputStream();
									ImageIO.write(result, "png", baos);
									imageData = baos.toByteArray();
									extension = "png";
								}
								if (extension.equalsIgnoreCase("jpeg")) {
									extension = "jpg";
								}
								final String cacheFileName = getImageCacheFileName(districtNum, name, (imgIdx++), extension);
								FileUtils.writeByteArrayToFile(new File(ATTACHMENTS_PATH, cacheFileName), imageData);
								candidateInfo.programImageFiles.add(cacheFileName);
							}
						}
					} else {
						System.out.println("\n - Note: " + name + " - has no link to program.");
					}
				}
				System.out.print(" Saving...");
				final String jsonPageInfo = gson.toJson(pageInfo);
				FileUtils.writeStringToFile(new File(CONTENT_PATH, "district_" + districtNum + ".json"), jsonPageInfo);
				System.out.println(" Done.");
			} else {
				System.out.println(" No data.");
			}
		}
		System.out.print("Finished successfully.");
	}

	public static String getImageCacheFileName(int districtNum, String candidateName, int imageIndex, String imageExtension) {
		return "district_" + districtNum + "_program_" + candidateName + "_image_" + imageIndex + "." + imageExtension;
	}

	public static InputStream getBinaryWithCaching(final String url) throws MalformedURLException, IOException {
		final File cachedFile = new File(new File(CACHE_PATH), urlToFileName(url));

		final InputStream result;
		if (!cachedFile.exists()) {
			final FileOutputStream fos = new FileOutputStream(cachedFile);
			try {
				IOUtils.copyLarge(new URL(url).openStream(), fos);
			} finally {
				try {
					fos.flush();
					fos.close();
				} catch (Exception e) {
				}
			}
		}
		result = new FileInputStream(cachedFile);

		return result;
	}

	public static Document get(int districtNum) throws IOException {
		return getHtmlWithCaching(getDistrictDataUrl(districtNum));
	}

	public static String getDistrictDataUrl(int districtNum) {
		return BASE_URL + DISTRICT_DATA_URL_PATTERN.replaceAll("%districtNum", String.valueOf(districtNum));
	}

	public static Document getHtmlWithCaching(final String url) throws IOException {
		final File cachedFile = new File(new File(CACHE_PATH), urlToFileName(url));

		final Document result;
		if (!cachedFile.exists()) {
			result = Jsoup.connect(url).timeout(60000).maxBodySize(0).get();
			final String content = result.html();
			FileUtils.writeStringToFile(cachedFile, content);
		} else {
			result = Jsoup.parse(cachedFile, "UTF-8");
		}

		return result;
	}

	protected static String urlToFileName(final String sourceName) {
		return sourceName.replaceAll("_", "__").replaceAll("[^A-Za-z0-9\\-\\_]", "_");
	}

	protected static List<String> extractNodesText(final Iterable<Node> nodes) {
		final List<String> nodesText = new ArrayList<String>();
		for (Node node : nodes) {
			if (node instanceof Element) {
				Element elem = (Element) node;
				if (elem.hasText()) {
					nodesText.add(elem.text());
				}
			} else if (node instanceof TextNode) {
				TextNode textNode = (TextNode) node;
				nodesText.add(textNode.text());
			}
		}
		return nodesText;
	}
}
