package tac.program.model;

import java.awt.Toolkit;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import tac.exceptions.InvalidNameException;
import tac.program.interfaces.AtlasInterface;
import tac.program.interfaces.CapabilityRenameable;
import tac.program.interfaces.LayerInterface;
import tac.program.interfaces.MapInterface;

public class AtlasTreeModel implements TreeModel {

	private static Logger log = Logger.getLogger(AtlasTreeModel.class);

	protected Atlas atlas;

	protected Set<TreeModelListener> listeners = new HashSet<TreeModelListener>();

	public AtlasTreeModel() {
		super();
		atlas = new Atlas();
	}

	public void addTreeModelListener(TreeModelListener l) {
		listeners.add(l);
	}

	public void removeTreeModelListener(TreeModelListener l) {
		listeners.remove(l);
	}

	public void notifyStructureChanged() {
		notifyStructureChanged(new TreeModelEvent(this, new Object[] { atlas }));
	}

	protected void notifyStructureChanged(TreeModelEvent event) {
		for (TreeModelListener l : listeners)
			l.treeStructureChanged(event);
	}

	public Object getChild(Object parent, int index) {
		if (parent instanceof AtlasInterface)
			return ((AtlasInterface) parent).getLayer(index);
		if (parent instanceof LayerInterface)
			return ((LayerInterface) parent).getMap(index);
		return null;
	}

	public int getChildCount(Object parent) {
		if (parent instanceof AtlasInterface)
			return ((AtlasInterface) parent).getLayerCount();
		if (parent instanceof LayerInterface)
			return ((LayerInterface) parent).getMapCount();
		return 0;
	}

	public int getIndexOfChild(Object parent, Object child) {
		return 0;
	}

	public Object getRoot() {
		return atlas;
	}

	public boolean isLeaf(Object node) {
		if (node instanceof MapInterface)
			return true;
		return false;
	}

	public void valueForPathChanged(TreePath path, Object newValue) {
		Object o = path.getLastPathComponent();
		boolean success = false;
		try {
			CapabilityRenameable sel = (CapabilityRenameable) o;
			String newName = (String) newValue;
			if (newName.length() == 0)
				return;
			sel.setName(newName);
			success = true;
		} catch (ClassCastException e) {
			log.error("", e);
		} catch (InvalidNameException e) {
			log.error(e.getLocalizedMessage());
			JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), "Renaming failed",
					JOptionPane.ERROR_MESSAGE);
		} finally {
			if (!success) {
				Toolkit.getDefaultToolkit().beep();
			}
		}
	}

	public Atlas getAtlas() {
		return atlas;
	}

	public void setAtlas(Atlas atlas) {
		this.atlas = atlas;
		notifyStructureChanged();
	}

}
