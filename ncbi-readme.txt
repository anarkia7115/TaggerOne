[Directory]

A. Versions
B. Contacts
C. Prerequisites
D. Instructions for evaluating TaggerOne
E. Preparing Ab3P
F. Instructions for processing text with TaggerOne
G. Instructions for training TaggerOne
H. Compiling TaggerOne
I. Known issues
J. References

#======================================================================================#

A. [Versions]

	0.2.1 Significantly reduced training time by improving score bound for NormalizationModel and AveragedNormalizationModel
	      Improved ProcessText to add ability to process text in Pubtator format; new "fileFormat" command line parameter required
	      Improvements to SynonymStringProcessor
	      Improvements to TabDelimitedLexiconMappingsLoader
	      Many minor improvements & bug fixes
	0.2.0 Initial public distribution

B. [Contacts]

	If you have any questions or problems, please e-mail robert.leaman@nih.gov

C. [Prerequisites]

	All scripts are prepared and tested for the Linux command line. Windows and OSX equivalents should be relatively straightforward, but are not provided.

	Running TaggerOne requires the Java runtime environment.

	Compiling TaggerOne also requires the Java SE Development Kit and ant.

	TaggerOne requires a substantial amount of main memory. Training typically requires 40Gb (forty gigabytes) while processing text using an existing model typically requires 20Gb. Scripts are configured to request an amount known to allow operation at reasonable speeds. 

D. [Instructions for evaluating TaggerOne]

	The TaggerOne download includes the NCBI Disease and BC5CDR corpora and models trained on their respective training data. Also included are scripts (EvalModel_*.sh) for each dataset/model. While TaggerOne requires many command-line configuration parameters, these scripts encapsulate the configuration needed for each dataset so that the user only needs to provide one: the name of the trained model to use. The evaluation commands for each dataset are:
	
	•	NCBI Disease: ./EvalModel_NCBID.sh output/model_NCBID.bin
	•	BC5CDR diseases: ./EvalModel_BC5CDRD.sh output/model_BC5CDRD.bin
	•	BC5CDR chemicals: ./EvalModel_BC5CDRC.sh output/model_BC5CDRC.bin
	
	The TaggerOne download also includes a model trained on the diseases of both the NCBI Disease and BC5CDR corpora. The commands for evaluating this model on the disease datasets are:
	
	•	NCBI Disease: ./EvalModel_NCBID.sh output/model_DISE.bin
	•	BC5CDR diseases: ./EvalModel_BC5CDRD.sh output/model_DISE.bin

E. [Preparing Ab3P]
	
	TaggerOne performs best when abbreviations have been resolved. TaggerOne currently uses the Ab3P abbreviation resolution tool, though implementing others should be straightforward. Evaluating TaggerOne on the included corpora does not require Ab3P to be installed, since its results have already been prepared in the various "abbreviations.tsv" files in the data folder. High performance on arbitrary text does require Ab3P to be installed.
	
	Download the Ab3P abbreviation resolution tool, version 1.5 from ftp://ftp.ncbi.nlm.nih.gov/pub/wilbur. Extract it into a folder accessible from the command line. The ProcessText.sh script must be changed so the AB3P_DIR parameter references this folder.
	
	Ab3P must be compiled after download. The command is "make", but it is recommended for you to open the readme file for Ab3P and follow the full instructions there.

	TaggerOne communicates with Ab3P through the file system. The scripts included use the "temp" folder in the root folder of the TaggerOne download.

F. [Instructions for processing text with TaggerOne]
	
	The TaggerOne download contains a script for applying a trained model to text without annotations to files in BioC format. The ProcessText.sh script must be modified to reflect the correct AB3P_DIR (see section E). The script requires 3 parameters:
	
	1. The name of the trained model.
	2. The name of the input file (BioC format).
	3. The name of the output file (BioC format).
	
G. [Instructions for training TaggerOne]
	
	The TaggerOne download contains scripts that encapsulate most of the command line configuration parameters needed to train TaggerOne. Each Train_*.sh script takes three parameters:
	
	1. The regularization parameter (the value c in section 2.3 of the manuscript). A value of 0.0 is equivalent to NO regularization.
	2. The max step size (the value m in section 2.3 of the manuscript). A value of 0.0 is equivalent to NO max step size.
	3. The model name prefix
	
	The training process evaluates the current model after each iteration through the training data and outputs it to disk if the performance has improved. You will typically want to pipe the output to a file so you can monitor its progress. The training commands, along with parameters that seem to work well, are:
	
	•	NCBI Disease: ./Train_NCBID.sh 10.0 1.5 output/model_NCBID 2>&1 | tee train_NCBID.log
	•	BC5CDR diseases: ./Train_BC5CDRD.sh 10.0 0.0 output/model_BC5CDRD 2>&1 | tee train_BC5CDRD.log
	•	BC5CDR chemicals: ./Train_BC5CDRC.sh 10.0 1.5 output/model_BC5CDRC 2>&1 | tee train_BC5CDRD.log
	
	Note that the regularization and max step sizes may benefit from more careful tuning. They certainly should be retuned if these scripts are adapted to be applied to other datasets. Also note that the performance of the trained model will vary slightly. 
	
	The TaggerOne download also includes a model trained on the diseases of both the NCBI Disease and BC5CDR corpora. The following command will retrain this model:
	
	•	NCBID & BC5CDR disease: ./Train_DISE.sh 10.0 1.5 output/model_DISE 2>&1 | tee train_DISE.log
	
	Also included is a script for training a single model for both the diseases and chemicals of the BC5CDR corpus. The resulting model is not included in the download, however. The following command will train the model:
	
	•	BC5CDR diseases & chemicals: ./Train_BC5CDRJ.sh 10.0 1.5 output/model_BC5CDRJ 2>&1 | tee train_BC5CDRJ.log
	
H. [Compiling TaggerOne]

	To compile TaggerOne after modifying the source files, execute "ant" from the root folder of the TaggerOne download.
	
I. [Known issues]
	
	If the scripts are downloaded and unpacked using Windows software, such as WinZip, then it is likely that the newlines were converted to DOS format. This causes errors during execution, typically "Error: Could not find or load main class"
	
	This can be corrected with the dos2unix command. Executing the following line will convert all scripts back to UNIX format:
	
	find . -name "*.sh" | xargs dos2unix
	
J. [References]

	Leaman R, Lu Z (2016) "TaggerOne: Joint Named Entity Recognition and Normalization with Semi-Markov Models", Bioinformatics, in press.
	