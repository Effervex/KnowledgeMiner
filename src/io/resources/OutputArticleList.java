package io.resources;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.ResourceAccess;

public class OutputArticleList {
	public static void main(String[] args) throws IOException {
		int minInLink = -1;
		if (args.length >= 2 && args[0].equals("-i"))
			minInLink = Integer.parseInt(args[1]);
		ResourceAccess.newInstance();
		WikipediaSocket wmi = ResourceAccess.requestWikipediaSocket();
		BufferedWriter out = new BufferedWriter(new FileWriter(new File(
				"articleList.txt")));
		int index = -1;
		do {
			index = wmi.getNextArticle(index);
			String type = wmi.getPageType(index);
			if (type != null && type.equals(WikipediaSocket.TYPE_ARTICLE)
					|| type.equals(WikipediaSocket.TYPE_DISAMBIGUATION)) {
				int numInLinks = wmi.getInLinks(index).size();
				if (numInLinks >= minInLink)
					out.write(index + "\t" + numInLinks + "\n");
			}
		} while (index != -1);
		out.close();
	}
}
