package servlets;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import server.TestHttpTileServer;
import tac.utilities.Png4BitWriter;

/**
 * 
 * Generates for each request a png tile of size 256x256 containing the url
 * request broken down to multiple lines.
 * 
 * @author r_x
 * 
 */
public class PngTileGeneratorServlet extends HttpServlet {

	private static final long serialVersionUID = 9054122976961610579L;

	private static final byte[] COLORS = { 0,// 
			(byte) 0xff, (byte) 0xff, (byte) 0xff, // white
			(byte) 0xff, (byte) 0x00, (byte) 0x00 // red
	};
	private static final IndexColorModel COLORMODEL = new IndexColorModel(8, 2, COLORS, 1, false);

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (TestHttpTileServer.errorResponse(response))
			return;
		BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_INDEXED,
				COLORMODEL);
		Graphics2D g2 = tile.createGraphics();
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, 255, 255);
		g2.setColor(Color.RED);
		g2.drawRect(0, 0, 255, 255);
		String url = request.getRequestURL().toString();
		url += "?" + request.getQueryString();
		System.out.println(url);
		String[] strings = url.split("[\\&\\?]");
		int y = 40;
		for (String s : strings) {
			g2.drawString(s, 2, y);
			y += 20;
		}
		g2.dispose();
		response.setContentType("image/png");
		OutputStream out = response.getOutputStream();
		Png4BitWriter.writeImage(out, tile, Deflater.NO_COMPRESSION);
		out.close();
	}
}
