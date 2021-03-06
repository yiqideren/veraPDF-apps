package org.verapdf.cli.multithread;

import org.verapdf.apps.Applications;
import org.verapdf.apps.utils.ApplicationUtils;
import org.verapdf.cli.commands.VeraCliArgParser;
import org.verapdf.processor.FormatOption;
import org.verapdf.processor.reports.ResultStructure;
import org.verapdf.processor.reports.multithread.MultiThreadProcessingHandler;
import org.verapdf.processor.reports.multithread.MultiThreadProcessingHandlerImpl;
import org.verapdf.processor.reports.multithread.writer.ReportWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultiThreadProcessor {
	private static final Logger LOGGER = Logger.getLogger(MultiThreadProcessor.class.getCanonicalName());

	private static final int DEFAULT_BUFFER_SIZE = 512;
	private static final int COEFFICIENT_BUFFER_SIZE = 1024;

	private final Queue<File> filesToProcess;

	private int filesQuantity;

	private File veraPDFStarterPath;
	private List<String> veraPDFParameters;
	private OutputStream os;
	private OutputStream errorStream;

	private ReportWriter reportWriter;
	private MultiThreadProcessingHandler processingHandler;

	private boolean isFirstReport = true;

	private MultiThreadProcessor(VeraCliArgParser cliArgParser) {
		this.os = new BufferedOutputStream(System.out, DEFAULT_BUFFER_SIZE * COEFFICIENT_BUFFER_SIZE);

		this.errorStream = new BufferedOutputStream(System.err, DEFAULT_BUFFER_SIZE);

		this.veraPDFStarterPath = getVeraPdfStarterFile(cliArgParser);
		this.veraPDFParameters = VeraCliArgParser.getBaseVeraPDFParameters(cliArgParser);
		this.filesToProcess = new ConcurrentLinkedQueue<>();
		this.filesToProcess.addAll(getFiles(cliArgParser.getPdfPaths(), cliArgParser.isRecurse()));
		this.filesQuantity = filesToProcess.size();

		FormatOption outputFormat = getOutputFormat(cliArgParser.getFormat().getOption());
		this.reportWriter = ReportWriter.newInstance(os, outputFormat, errorStream);
		this.processingHandler = new MultiThreadProcessingHandlerImpl(reportWriter);
	}

	public static void process(VeraCliArgParser cliArgParser) {
		MultiThreadProcessor processor = new MultiThreadProcessor(cliArgParser);
		processor.startProcesses(cliArgParser.getNumberOfProcesses());
	}

	private File getVeraPdfStarterFile(VeraCliArgParser cliArgParser) {
		File veraPDFPath = cliArgParser.getVeraCLIPath();
		if (veraPDFPath == null || !veraPDFPath.isFile()) {
			try {
				veraPDFPath = Applications.getVeraScriptFile();
				if (veraPDFPath == null) {
					throw new IllegalStateException("Can't obtain executable veraPDF CLI script path");
				}
			} catch (IllegalStateException e) {
				LOGGER.log(Level.SEVERE, "Can't obtain veraPDF CLI script path", e);
			}
		}
		return veraPDFPath;
	}

	private FormatOption getOutputFormat(String outputFormat) {
		return FormatOption.fromOption(outputFormat);
	}

	public synchronized void write(ResultStructure result) {
		if (isFirstReport) {
			processingHandler.startReport();
			processingHandler.fillReport(result);
			isFirstReport = false;
		} else {
			processingHandler.fillReport(result);
		}

		this.filesQuantity--;

		if (filesQuantity == 0) {
			processingHandler.endReport();
		}
	}

	private List<File> getFiles(List<String> pdfPaths, boolean isRecurse) {
		List<File> toFilter = new ArrayList<>(pdfPaths.size());
		pdfPaths.forEach(path -> toFilter.add(new File(path)));

		return ApplicationUtils.filterPdfFiles(toFilter, isRecurse);
	}

	private void startProcesses(int numberOfProcesses) {
		int processesQuantity = Math.min(numberOfProcesses, filesToProcess.size());
		for (int i = 0; i < processesQuantity; i++) {
			BaseCliRunner veraPDFRunner = new BaseCliRunner(this, veraPDFStarterPath.getAbsolutePath(), veraPDFParameters, filesToProcess);
			new Thread(veraPDFRunner).start();
		}
	}

}
