package tac.gui.components;

import java.util.Vector;

import org.apache.log4j.Logger;

public class JMapSizeCombo extends JIntCombo {

	private static final long serialVersionUID = 1L;

	public static final int MIN = 1024;

	public static final int MAX = 32767;

	static Vector<Integer> MAP_SIZE_VALUES;

	static Integer DEFAULT;

	static Logger log = Logger.getLogger(JMapSizeCombo.class);

	static {
		// Sizes from 1024 to 32768
		MAP_SIZE_VALUES = new Vector<Integer>(10);
		MAP_SIZE_VALUES.addElement(new Integer(32767));
		MAP_SIZE_VALUES.addElement(new Integer(30000));
		MAP_SIZE_VALUES.addElement(new Integer(25000));
		MAP_SIZE_VALUES.addElement(new Integer(20000));
		MAP_SIZE_VALUES.addElement(new Integer(15000));
		MAP_SIZE_VALUES.addElement(new Integer(10000));
	}

	public JMapSizeCombo() {
		super(MAP_SIZE_VALUES, DEFAULT);
		setEditable(true);
		setEditor(new Editor());
		setMaximumRowCount(MAP_SIZE_VALUES.size());
		setSelectedItem(DEFAULT);
	}

	@Override
	protected void createEditorComponent() {
		editorComponent = new JIntField(MIN, MAX, 4, "<html>Invalid map size!<br>"
				+ "Please enter a number between %d and %d</html>");
	}

}