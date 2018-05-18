/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.languageserver;

import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Assistant.ASSISTANT;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Assistant.FIND_DEFINITION;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Project.New.FILE;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Project.New.NEW;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Project.PROJECT;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Workspace.CREATE_PROJECT;
import static org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants.Workspace.WORKSPACE;
import static org.eclipse.che.selenium.pageobject.CodenvyEditor.MarkerLocator.ERROR;
import static org.eclipse.che.selenium.pageobject.CodenvyEditor.MarkerLocator.WARNING;
import static org.eclipse.che.selenium.pageobject.Wizard.SamplesName.CONSOLE_PYTHON3_SIMPLE;
import static org.openqa.selenium.Keys.F4;

import com.google.inject.Inject;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.core.workspace.WorkspaceTemplate;
import org.eclipse.che.selenium.pageobject.AskForValueDialog;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.Wizard;
import org.eclipse.che.selenium.pageobject.dashboard.Dashboard;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.openqa.selenium.Keys;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Skoryk Serhii */
public class CheckMainFeatureForPythonLanguageTest {
  private static final String PROJECT_NAME = "console-python3-simple";
  private static final String PYTHON_FILE_NAME = "main.py";
  private static final String PYTHON_MODULE_FILE_NAME = "math.py";
  private static final String LS_INIT_MESSAGE =
      "Initialized Language Server org.eclipse.che.plugin.python.languageserver on project file:///projects/console-python3-simple";
  private static final String PYTHON_CLASS =
      "class MyClass:\n"
          + "\tvar = 1\n"
          + "variable = \"variable\"\n"
          + "\n"
          + "def function(self):\n"
          + "\tprint(\"This is a message inside the class.\")";

  @InjectTestWorkspace(template = WorkspaceTemplate.PYTHON)
  private TestWorkspace workspace;

  @Inject private Ide ide;
  @Inject private Menu menu;
  @Inject private Wizard wizard;
  @Inject private Consoles consoles;
  @Inject private Dashboard dashboard;
  @Inject private CodenvyEditor editor;
  @Inject private CommandsPalette commandsPalette;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private AskForValueDialog askForValueDialog;

  @BeforeClass
  public void setUp() throws Exception {
    ide.open(workspace);
  }

  @Test
  public void checkLanguageServerInitialized() {
    ide.waitOpenedWorkspaceIsReadyToUse();

    menu.runCommand(WORKSPACE, CREATE_PROJECT);
    wizard.selectProjectAndCreate(CONSOLE_PYTHON3_SIMPLE, PROJECT_NAME);

    projectExplorer.waitItem(PROJECT_NAME);
    projectExplorer.openItemByPath(PROJECT_NAME);
    projectExplorer.openItemByPath(PROJECT_NAME + "/main.py");
    editor.waitTabIsPresent(PYTHON_FILE_NAME);

    consoles.selectProcessByTabName("dev-machine");
    System.out.println(consoles.getVisibleTextFromCommandConsole());
    // consoles.waitExpectedTextIntoConsole(LS_INIT_MESSAGE);
  }

  @Test(priority = 1)
  public void checkErrorMessages() {
    editor.selectTabByName(PYTHON_FILE_NAME);
    editor.deleteAllContent();
    editor.typeTextIntoEditor(PYTHON_CLASS);
    editor.typeTextIntoEditor("\n");
    editor.waitMarkerInPosition(WARNING, editor.getPositionVisible());
    // TODO check for "W293 blank line contains whitespace" message in WARNING marker

    editor.goToCursorPositionVisible(1, 1);
    editor.waitAllMarkersInvisibility(ERROR);
    editor.typeTextIntoEditor("c");
    editor.waitMarkerInPosition(ERROR, 1);
    editor.typeTextIntoEditor(Keys.DELETE.toString());
    editor.waitAllMarkersInvisibility(ERROR);
  }

  @Test(priority = 1)
  public void checkAutocompleteFeature() {
    editor.selectTabByName(PYTHON_FILE_NAME);
    editor.deleteAllContent();
    editor.typeTextIntoEditor(PYTHON_CLASS);

    editor.typeTextIntoEditor("\n");
    editor.goToPosition(editor.getPositionVisible(), 1);
    editor.typeTextIntoEditor("object = MyClass()\nprint(object.");

    // check contents of autocomplete container
    editor.launchAutocompleteAndWaitContainer();
    editor.waitTextIntoAutocompleteContainer("function");
    editor.waitTextIntoAutocompleteContainer("var");
    editor.waitTextIntoAutocompleteContainer("variable");

    // TODO check autocomplete
    editor.enterAutocompleteProposal("function() ");
    editor.waitTextIntoEditor("print(object.function");
    editor.typeTextIntoEditor("())");
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByDoubleClick(PROJECT_NAME + ": run");
    consoles.waitTabNameProcessIsPresent(PROJECT_NAME + ": run");
    consoles.waitExpectedTextIntoConsole("This is a message inside the class.");
  }

  @Test(priority = 1)
  public void checkFindDefinitionFeature() {
    createFile(PYTHON_MODULE_FILE_NAME);
    editor.selectTabByName(PYTHON_MODULE_FILE_NAME);
    editor.typeTextIntoEditor("def add(a, b):\n return a + b");
    editor.clickOnCloseFileIcon(PYTHON_MODULE_FILE_NAME);

    editor.selectTabByName(PYTHON_FILE_NAME);
    editor.deleteAllContent();
    editor.typeTextIntoEditor("import math\n");
    // editor.typeTextIntoEditor(PYTHON_CLASS);
    editor.typeTextIntoEditor("\nvar2 = math.add(100, 200)");

    // check Find Definition feature from Assistant menu
    editor.goToPosition(editor.getPositionVisible(), 15);
    menu.runCommand(ASSISTANT, FIND_DEFINITION);
    editor.waitTabIsPresent(PYTHON_MODULE_FILE_NAME);
    editor.clickOnCloseFileIcon(PYTHON_MODULE_FILE_NAME);

    // check Find Definition feature by pressing F4
    editor.goToPosition(editor.getPositionVisible(), 15);
    editor.typeTextIntoEditor(F4.toString());
    editor.waitTabIsPresent(PYTHON_MODULE_FILE_NAME);
  }

  private void createFile(String fileName) {
    projectExplorer.waitAndSelectItem(PROJECT_NAME);
    menu.runCommand(PROJECT, NEW, FILE);
    askForValueDialog.waitFormToOpen();
    askForValueDialog.typeAndWaitText(fileName);
    askForValueDialog.clickOkBtn();
    askForValueDialog.waitFormToClose();
    editor.waitTabIsPresent(fileName);
  }
}
