/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.ui.editor.preferences;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.xtext.ui.editor.preferences.LanguageRootPreferencePage;
import org.grammaticalframework.eclipse.GFPreferences;
import org.grammaticalframework.eclipse.ui.internal.GFActivator;

/**
 * Root preference page
 */
public class GFLanguageRootPreferencePage extends LanguageRootPreferencePage {
	
	/**
	 * The Constant PAGE_DESCRIPTION.
	 */
	private static final String PAGE_DESCRIPTION = "Settings for GF development."; //$NON-NLS-1$
	
	/* (non-Javadoc)
	 * @see org.eclipse.xtext.ui.editor.preferences.LanguageRootPreferencePage#createFieldEditors()
	 */
	@Override
	protected void createFieldEditors() {
		Composite parent = getFieldEditorParent();
		addField(new StringFieldEditor(GFPreferences.GF_BIN_PATH, "&Runtime path:", parent));
		addField(new StringFieldEditor(GFPreferences.GF_LIB_PATH, "&Library path:", parent));
		String[][] entryNamesAndValues = new String[][]{
			// Refer: http://logging.apache.org/log4j/1.2/manual.html
			{"All", "DEBUG"},
			{"Info", "INFO"},
			{"None", "OFF"},
		};
		addField(new ComboFieldEditor(GFPreferences.LOG_LEVEL, "Log Le&vel:", entryNamesAndValues, parent));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.xtext.ui.editor.preferences.AbstractPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(GFActivator.getInstance().getPreferenceStore());
		setDescription(PAGE_DESCRIPTION);
	}
	
}
