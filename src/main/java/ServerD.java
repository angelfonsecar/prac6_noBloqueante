import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerD {
    private String dirActual;
    private String raiz;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    public ServerD(){
        try{
            ServerSocket s = new ServerSocket(8001);
            s.setReuseAddress(true);
            System.out.println("Servidor iniciado esperando por archivos...");
            File f = new File("");
            String ruta = f.getAbsolutePath();
            String carpeta="drive";
            raiz = ruta+"\\"+carpeta+"\\";
            dirActual = raiz;
            System.out.println("ruta:"+raiz);
            File f2 = new File(raiz);
            f2.mkdirs();
            f2.setWritable(true);


            for(;;){
                Socket cl = s.accept();
                System.out.println("Cliente conectado desde "+cl.getInetAddress()+":"+cl.getPort());

                oos = new ObjectOutputStream(cl.getOutputStream());
                ois = new ObjectInputStream(cl.getInputStream());
                mostrarArchivos();

                while(true){//bucle de escucha de instrucciones
                    int elec = (int) ois.readObject();
                    if(elec==0) break;
                    switch (elec) {
                        case 1 -> {   //subir archivo o carpeta
                            subir();
                            mostrarArchivos();
                        }
                        case 2 -> {
                            descargarC();
                            mostrarArchivos();
                        }
                        case 3 -> {
                            eliminar();
                            mostrarArchivos();
                        }
                        case 4 -> {
                            System.out.println("cambiar dir");
                            cambiarDir();
                        }
                    }
                }
                oos.close();
                ois.close();
                cl.close();
            }//for

        }catch(Exception e){
            e.printStackTrace();
        }
    }
    public void descargarC() throws IOException, ClassNotFoundException{
        String elecdow = (String) ois.readObject();
        File fileToDowload = new File(dirActual+"\\"+elecdow);
        if(fileToDowload.exists()){
            System.out.println("Descargando "+fileToDowload.getAbsolutePath());
            oos.writeObject("Descargando" +fileToDowload.getAbsolutePath());
            oos.flush();
            if (!fileToDowload.isDirectory()) {  //descargar archivo
                descarga(fileToDowload);
            } else {        //eliminar directorio
                String nomzip = fileToDowload.getAbsolutePath()+".zip";
                System.out.println("nombre:" +nomzip);
                ZipUtil.pack(fileToDowload, new File(nomzip));
                descarga(new File(nomzip));
                new File(nomzip).delete();
            }
        }else {
            oos.writeObject("El archivo o dir no existe");
            oos.flush();
        }
    }

    public void descarga(File f) throws IOException {
        long tam = f.length();
        System.out.println("Enviando '"+f.getName()+"' de "+tam/1024+" kb");

        oos.writeObject(f);
        oos.flush();

        DataInputStream disf = new DataInputStream(new FileInputStream(f.getAbsolutePath()));

        long enviados = 0;
        int l;
        while (enviados<tam){
            byte[] b = new byte[1500];
            l=disf.read(b);
            oos.write(b, 0, l);
            oos.flush();
            enviados += l;
        }
        disf.close();
        System.out.println("Archivo enviado");
    }

    public void mostrarArchivos() throws IOException {
        File f = new File(dirActual);
        File []listaDeArchivos = f.listFiles();
        oos.writeObject(listaDeArchivos);
        oos.flush();
    }

    public void subir() throws IOException, ClassNotFoundException {
        File f = (File)ois.readObject(); //archivo que recibo

        String fileName = f.toString();
        String aux = FilenameUtils.getExtension(fileName);

        if(aux.equals("zip")) {
            System.out.println("\nEl cliente quiere subir carpeta");
            subirDir(f);
        }
        else {
            System.out.println("\nEl cliente quiere subir un archivo");
            subirArchivo(f);
        }
    }

    public void subirArchivo(File f) throws IOException {
        long tam = f.length();

        System.out.println("Comienza descarga del archivo '"+f.getName()+"' de "+tam/1024+" kb");
        System.out.println("Subiendo a "+dirActual);
        DataOutputStream dosf = new DataOutputStream(new FileOutputStream(dirActual+f.getName()));

        byte[] buffer = new byte[1024];
        int bufferLength = 0;
        long recibidos=0;
        int l;
        while(recibidos<tam){
            byte[] b = new byte[1500];
            l = ois.read(b,0,b.length);
            dosf.write(b,0,l);
            dosf.flush();
            recibidos += l;
        }//while

        System.out.println("Archivo recibido");
        dosf.close();
    }
    public void subirDir(File f) throws IOException{

        System.out.println("carpeta a subir:"+f.getName());
        String destino = Paths.get(dirActual, f.getName()).toString();

        subirArchivo(f);

        Path descom = Paths.get(dirActual, FilenameUtils.removeExtension(f.getName()) );

        System.out.println("Descomprimiendo " + destino + " en " + descom.toString());

        new net.lingala.zip4j.ZipFile(destino).extractAll(descom.toString());
        new File(destino).delete();
    }



    public void eliminar() throws IOException, ClassNotFoundException {     //trabajo en este
        String elec = (String) ois.readObject();
        File fileToDelete = new File(dirActual+"\\"+elec);
        if(fileToDelete.exists()){
            System.out.println("eliminando "+fileToDelete.getAbsolutePath());
            if (!fileToDelete.isDirectory()) {  //eliminar archivo
                if(fileToDelete.delete()) {
                    oos.writeObject("Archivo eliminado");
                    oos.flush();
                }
                else {
                    oos.writeObject("Error al eliminar archivo");
                    oos.flush();
                }
            }
            else {        //eliminar directorio
                if(deleteDirectory(fileToDelete)) {
                    oos.writeObject("Dir eliminado");
                    oos.flush();
                }
                else {
                    oos.writeObject("Error al eliminar dir");
                    oos.flush();
                }
            }
        }else {
            oos.writeObject("El archivo o dir no existe");
            oos.flush();
        }
    }
    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents)
                deleteDirectory(file);
        }
        return directoryToBeDeleted.delete();
    }

    public void cambiarDir() throws IOException, ClassNotFoundException {   //estoy trabajando en este
        String elec = (String) ois.readObject();
        File f = new File(dirActual+"\\"+elec);

        boolean existeDir = f.isDirectory() || elec.equals("..");
        if(!existeDir){  //comprobamos que el directorio solicitado existe
            elec = "";
            System.out.println("Dir invalido");
        }
        oos.writeObject(existeDir);
        oos.flush();
        System.out.println("Entrar a '"+elec+"'");
        if(elec.equals("..")){
            dirActual = raiz;
        }else if(!elec.equals("")){
            dirActual = dirActual + elec + "\\";
        }
        mostrarArchivos();
    }

    public static void main(String[] args){
        new ServerD();
    }//main
}