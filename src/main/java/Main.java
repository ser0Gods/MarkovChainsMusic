import jm.audio.io.SampleOut;
import jm.audio.synth.EnvPoint;
import jm.audio.synth.Envelope;
import jm.audio.synth.Oscillator;
import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;


public class Main extends jm.audio.Instrument {
    float[] transfer;
    private HashMap<Integer, String> tunes2notes = new HashMap<>();
    private HashMap<String, Integer> notes2tunes = new HashMap<>();
    private ArrayList<String> noteNames = new ArrayList<>();
    private HashMap<Integer, Float> tuneFreq = new HashMap<>();
    private HashMap<String, HashMap<String, Integer>> matrix = new HashMap<>();
    private Integer max = 0;
    private int[][] transitionMatrix;
    private ArrayList<Integer> tunes;
    private int[] followers;

    public static void main(String[] args) {
        Main a = new Main();
        float[] data = a.readAndDisplay("Storms.wav");
        int startIndex = a.getStartIndex(data);
        int endIndex = a.getEndIndex(data);
        float[] dataTrimed = a.trimData(data, startIndex, endIndex);
        a.transfer = dataTrimed;
//        float[] reduced = a.getSplit(dataTrimed, 2);
        a.writeToFile(dataTrimed, "tmp.wav");
        a.tunes = a.getNotes(dataTrimed);
        a.convertToNotes(a.tunes);
        a.reproduce(a.tunes, dataTrimed);

        a.buildMatrix(a.noteNames);
        a.writeMatrixToFile(a.transitionMatrix);

        a.generateMusicForXSteps(dataTrimed.length/4, "generated.wav");
    }

    private void writeMatrixToFile(int[][] transitionMatrix) {
        Document dom;

        // instance of a DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            // use factory to get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            // create instance of DOM
            dom = db.newDocument();

            // create the root element
            Element rootEle = dom.createElement("transitionMatrix");

            // create data elements and place them under root
            for (int i = 0; i < transitionMatrix.length; i++) {
                Element from = dom.createElement(this.tunes2notes.get(i));
                for (int j = 0; j < transitionMatrix[i].length; j++) {
                    Element to = dom.createElement(this.tunes2notes.get(j));
                    to.appendChild(dom.createTextNode(String.valueOf(this.transitionMatrix[i][j])));
                    from.appendChild(to);
                }
                rootEle.appendChild(from);
            }

            dom.appendChild(rootEle);

            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty(OutputKeys.METHOD, "xml");
                tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
//                tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
//                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                // send DOM to file
                tr.transform(new DOMSource(dom),
                        new StreamResult(new FileOutputStream("transitionMatrix.xml")));

            } catch (TransformerException te) {
                System.out.println(te.getMessage());
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
        } catch (ParserConfigurationException pce) {
            System.out.println("UsersXML: Error trying to instantiate DocumentBuilder " + pce);
        }
    }

    private void generateMusicForXSteps(int steps, String filename) {
        this.calculateNumberOfFollowingTunes();
        ArrayList<Integer> song = new ArrayList<>();
        Random rnd = new Random();
        int tune = -1;
        Integer[] tunes = new Integer[this.tunes2notes.keySet().size()];
        this.tunes2notes.keySet().toArray(tunes);

        //berechne zufälligen start ton
        do {
            float rng = rnd.nextFloat();
//            System.out.println("Random Number: " + rng);
            tune = (int) ((rng * 1379.0f) % this.max);
//            System.out.println(tune);
        } while (!(tune > 0) || !(this.followers[tune] > 0) || !(this.tunes.contains(tune)));
        System.out.println("Start tune: " + this.tunes2notes.get(tune) + "/" + tune);
        song.add(tune);

        int next = -1;
        for (int j = 0; j < steps - 1; j++) {
//            System.out.println("Number of followers: "+this.followers[tune]);
            do {
                next = this.getNextTune(tune);
            } while (!(next > 0) || !(this.followers[next] > 0) || !(this.tunes.contains(next)));
//            System.out.println("choosen next: "+next);
            song.add(next);
            tune = next;
        }
        System.out.println("Song size: " + song.size());
        float[] songFrequencies = this.convertTunesToFrequences(song);
        Write.audio(songFrequencies, "songFrequences.wav");

        this.convertToRemix(song,100);
    }

    private void convertToRemix(ArrayList<Integer> song, int length) {
        Score score = new Score("myScore");
        Part part = new Part("Part");
        Phrase phrase = new Phrase("Phrase");

        for (int i = 0; i < length; i++) {
            phrase.addNote(new Note(song.get(i),SIXTEENTH_NOTE));
        }

        part.add(phrase);
        score.add(part);
        Write.au(score,"remix.wav",this);
    }

    private void calculateNumberOfFollowingTunes() {
        this.followers = new int[this.max + 1];
        for (int i = 0; i < followers.length; i++) {
            for (int count : this.transitionMatrix[i]) {
                if (count > 0)
                    followers[i] = followers[i] + 1;
            }
        }
        for (int i = 0; i < followers.length; i++) {
            System.out.println(i + ": " + followers[i]);
        }
    }

    private float[] convertTunesToFrequences(ArrayList<Integer> song) {
        float[] frequencies = new float[song.size()];
        for (int i = 0; i < song.size(); i++) {
            frequencies[i] = this.tuneFreq.get(song.get(i));
        }
//        for (float freq :frequencies) {
//            System.out.println(freq);
//        }
        return frequencies;
    }

    private int getNextTune(int tune) {
        int[] transitionFromTune = this.transitionMatrix[tune];

        if (!(this.followers[tune]>0)) {
            return 0;
        }
        int total = 0;
        for (int follow : transitionFromTune) {
            total += follow;
        }
        //Roll 9 times for better randomness
        Random rng = new Random();
        int counter = 0;
        for (int i = 0; i < 9; i++) {
            int roll = rng.nextInt(total) + 1;
            counter += roll;
        }
        counter = counter % total;
        for (int i = 0; i < transitionFromTune.length; i++) {
            if (transitionFromTune[i] >= counter) {
//                System.out.println("Next tune: "+this.tunes2notes.get(i)+"/"+i+" with a chance of "+((float)counter)/((float)total));
                return i;
            } else {
                counter = counter - transitionFromTune[i];
            }
        }
        return -1;
    }

    private int[][] buildMatrix(ArrayList<String> noteNames) {
        this.transitionMatrix = new int[this.max + 1][this.max + 1];
        System.out.println("max: " + this.max);

        //transition matrix mit Nullen vor befuellen
        for (int[] sub : transitionMatrix) {
            Arrays.fill(sub, 0);
        }


        for (int i = 0; i < this.tunes.size() - 1; i++) {
//            System.out.println(this.tunes.get(i)+" -> "+this.tunes.get(i+1));
            this.transitionMatrix[this.tunes.get(i)][this.tunes.get(i + 1)] += 1;
        }
        System.out.println("Size of Lookup-Table: " + transitionMatrix.length);
        return transitionMatrix;
    }

    private void convertToNotes(ArrayList<Integer> tunes) {
        for (Field f : Pitches.class.getDeclaredFields()) {
            try {
                tunes2notes.put(f.getInt(new Object()), f.getName());
                notes2tunes.put(f.getName(), f.getInt(new Object()));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        for (Integer tune : tunes) {
            noteNames.add(tunes2notes.get(tune));
        }

        for (Integer mytune : tunes) {
            if (mytune > this.max) {
                this.max = mytune;
            }
        }

//        for (String noteName : noteNames) {
//            System.out.println(noteName);
//        }
    }

    private float[] getSplit(float[] dataTrimed, int steps) {
        float[] result = new float[dataTrimed.length / steps + steps];
        for (int j = 0; j < dataTrimed.length; j += steps) {
            result[j / steps] = dataTrimed[j];
        }
        return result;
    }

    private void reproduce(ArrayList<Integer> tunes, float[] reduced) {
        System.out.println(tunes.size() + " == " + reduced.length);
        for (int i = 0; i < tunes.size(); i++) {
            tuneFreq.put(tunes.get(i), reduced[i]);
        }

//        for (Integer key : tuneFreq.keySet()) {
//            System.out.println(key + " : " + tuneFreq.get(key));
//        }

        float[] song = new float[tunes.size()];
        for (int i = 0; i < tunes.size(); i++) {
            song[i] = tuneFreq.get(tunes.get(i));
        }
        Write.audio(song, "song.wav");
    }

    private ArrayList<Integer> getNotes(float[] reduced) {
        HashMap<Integer, Integer> tuneConstellation = new HashMap<>();
        ArrayList<Integer> tunes = new ArrayList<>();
        for (int i = 0; i < reduced.length; i += 1) {
            int offset = 0;
            for (int j = 0; j - 40 < reduced[i] * 40; j++) {
                offset++;
            }
            tunes.add(offset);
            if (tuneConstellation.containsKey(offset)) {
                tuneConstellation.put(offset, tuneConstellation.get(offset) + 1);
            } else {
                tuneConstellation.put(offset, 1);
            }
        }
        int size = 0;
//        for (Integer key : tuneConstellation.keySet()) {
//            Integer tmp = tuneConstellation.get(key);
//            size += tmp;
//            System.out.println(key + " : " + tmp);
//        }
        System.out.println("size: " + size);
        return tunes;
    }

    private float[] trimData(float[] data, int startIndex, int endIndex) {
        float[] dataTrimed = new float[endIndex - startIndex];
        int index = 0;
        for (int i = startIndex; i < endIndex; i++, index++) {
            dataTrimed[index] = data[i];
        }
        System.out.println("Trimed data lenght: " + dataTrimed.length);

        return dataTrimed;
    }

    private int getEndIndex(float[] data) {
        int endIndex = data.length;
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == 0.0f) {
                endIndex = i - 1;
            } else {
                break;
            }
        }
        System.out.println("endIndex: " + endIndex);
        return endIndex;
    }

    private int getStartIndex(float[] data) {
        int startIndex = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0.0f) {
                startIndex = i + 1;
            } else {
                break;
            }
        }
        System.out.println("startIndex: " + startIndex);
        return startIndex;
    }

    private void writeToFile(float[] data, String filename) {
        Write.audio(data, filename);
    }

    public float[] readAndDisplay(String filename) {
        float[] data = Read.audio(filename);
//        for (int i = 0; i < data.length; i++) {
//            System.out.println(data[i]);
//        }
        System.out.println("data: " + data.length);
        return data;
    }

    @Override
    public void createChain(){
        Oscillator wt = new Oscillator(this, Oscillator.SINE_WAVE,
                44100, 1);

        Envelope env = new Envelope(wt, new EnvPoint[] {
                new EnvPoint((float)0.0, (float)0.0),
                new EnvPoint((float)0.02, (float)1.0),
                new EnvPoint((float)0.15, (float)0.6),
                new EnvPoint((float)0.9, (float)0.4),
                new EnvPoint((float)1.0, (float)0.0)

        });
        SampleOut sout = new SampleOut( env, "jmusic.tmp");
    }
}
