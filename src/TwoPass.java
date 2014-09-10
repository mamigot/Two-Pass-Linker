import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// scp -r TwoPassLinker/ ma2786@i5.nyu.edu:
public class TwoPass {

	enum Data {
		DEFINITIONS, USES, INSTRUCTIONS;

		static Data current = getFirst();

		public static Data getFirst() {
			return current = Data.DEFINITIONS;
		}

		public static Data getNext() {
			if (current == Data.DEFINITIONS)
				return current = Data.USES;

			else if (current == Data.USES)
				return current = Data.INSTRUCTIONS;

			else
				return current = Data.DEFINITIONS;
		}
	}

	class Symbol {
		public String symbol;
		public int location;
		public boolean isUsed;

		// Specifies the module wherein it was defined
		public Module defined;
		// Specifies the modules in which it was used
		public List<Module> used = new ArrayList<Module>();
	}

	class TextInstruction {
		public char classification;
		public int opcode;
		public int address;

		public String toString() {
			return (this.opcode * 1000) + this.address + "";
		}
	}

	class Module {
		// Position of the module with respect to the others
		// (if it's the first, 0, then 1... useful as an identifier)
		public int position;

		public int startLocation;
		public int endLocation;
		public int length;
		public List<TextInstruction> textInstructions = new ArrayList<TextInstruction>();
	}

	ArrayList<Module> modules = new ArrayList<Module>();
	ArrayList<Symbol> symbols = new ArrayList<Symbol>();
	// Incremented as more modules are processed
	int memoryAddressCounter = 0;
	/**
	 * Last visited item. Since an instruction such as "R 1002" is processed in
	 * two iterations, this global variable is meant to hold the reference to
	 * the item whose information is being gathered; could be an instruction or
	 * a definition.
	 */
	Object incompleteItem;

	public TwoPass(String inputFilePath) throws FileNotFoundException {
		// Save all of the content into a variable
		Scanner scanner = new Scanner(new File(inputFilePath));
		String content = scanner.useDelimiter("\\A").next();
		// Identifies the tokens using RegEx
		Matcher matcher = Pattern.compile("[\\d\\w]+").matcher(content);

		// Type that will be visited first
		Data nextType = Data.getFirst();

		int remainingDefinitions = 0;
		int remainingUses = 0;
		int remainingInstructions = 0;

		String token;
		while (matcher.find()) {
			token = matcher.group();

			if (remainingDefinitions > 0) {
				if (remainingDefinitions % 2 == 0)
					this.processDefSymbol(token);
				else
					this.processDefLocation(token);

				remainingDefinitions--;
				if (remainingDefinitions == 0)
					nextType = Data.getNext();
				continue;
			}

			else if (remainingUses > 0) {
				this.processUseSymbol(token);

				remainingUses--;
				if (remainingUses == 0)
					nextType = Data.getNext();
				continue;
			}

			else if (remainingInstructions > 0) {
				this.processInstructionElement(token);

				remainingInstructions--;
				if (remainingInstructions == 0)
					nextType = Data.getNext();
				continue;
			}

			// Only case left: a "remaining" counter
			// (the numbers before the definitions, uses, instructions...)
			else {
				// Check whatever nextType is and update that value
				int numNewElements = Integer.parseInt(token);
				if (numNewElements == 0)
					nextType = Data.getNext();

				if (nextType == Data.DEFINITIONS)
					remainingDefinitions = numNewElements * 2;

				else if (nextType == Data.USES)
					remainingUses = numNewElements;

				else if (nextType == Data.INSTRUCTIONS)
					remainingInstructions = numNewElements * 2;
			}
		}
	}

	private void processDefSymbol(String element) {

		System.out.println("def symbol:" + element);

	}

	private void processDefLocation(String element) {

		System.out.println("def locati:" + element);

	}

	private void processUseSymbol(String element) {

		System.out.println("use symbol:" + element);

	}

	/**
	 * Could be a classification character (I, A, R, E) or an opcode + address.
	 */
	private void processInstructionElement(String element) {

		if ( !this.isInteger(element) ) {
			System.out.println("inst chara:" + element);

		} else {
			System.out.println("inst addre:" + element);
			
		}

	}

	private boolean isInteger(String element) {
		try {
			Integer.parseInt(element);
			return true;

		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static void main(String[] args) throws IOException {

		String filePath = "inputs/input-2.txt";

		TwoPass tp = new TwoPass(filePath);

	}

}
