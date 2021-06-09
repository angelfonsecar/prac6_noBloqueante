import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;
import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Scanner;

public class NIOclient {
    private String dirActual = "drive";
    private Selector selector;
    private String ruta;

    public NIOclient() {
        try {
            selector = Selector.open();
            SocketChannel client = SocketChannel.open();
            client.configureBlocking(false);
            client.connect(new InetSocketAddress("127.0.0.1", 3500));
            client.register(selector, SelectionKey.OP_CONNECT);

            while (true) {

                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (key.isConnectable()) {
                        if (client.isConnectionPending()) {
                            System.out.println("Intentando establecer la conexion\n");
                            try {
                                client.finishConnect();
                                System.out.println("Conexion establecida ;)\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        client.register(selector, SelectionKey.OP_READ);
                        continue;
                    }
                    if (key.isReadable()) {
                        mostrarArchivos(client);
                        client.register(selector, SelectionKey.OP_WRITE);
                        key.interestOps(SelectionKey.OP_WRITE);
                        continue;
                    }
                    if (key.isWritable()){
                        int elec;
                        Scanner reader = new Scanner(System.in);
                        do{
                            System.out.println("\nMenu\n1-Subir un archivo o carpeta\n2-Descargar\n3-Eliminar un archivo o carpeta\n4-Cambiar directorio\n0-Salir\n\n>");
                            elec= reader.nextInt();
                            ByteBuffer bb = ByteBuffer.allocate(4).putInt(elec);
                            bb.flip();
                            client.write(bb);

                            switch (elec) {
                                case 1 -> {   //subir archivo o carpeta
                                    System.out.println("Lanzando JFileChooser...");
                                    subir(client);
                                    mostrarArchivos(client);
                                }
                                case 2 -> {   //descargar un archivo o carpeta
                                    System.out.println("Descargar un archivo o carpeta");
                                    descargar(client);
                                }
                                case 3 -> {   //eliminar archivo o carpeta
                                    System.out.println("Eliminar");
                                    eliminar(client);
                                    mostrarArchivos(client);
                                }
                                case 4 -> cambiarDir(client);   //cambiar directorio
                            }
                        }while(elec!=0);
                        System.out.println("Saliendo...");
                        client.close();
                        selector.close();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }//catch
    }

    private void escribeObjeto(Object o, SocketChannel client) throws IOException {    //Envía un objeto al canal
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(baos);
        oos2.writeObject(o);
        oos2.flush();
        byte[] b = baos.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(b);
        client.write(buffer);
        oos2.close();
        baos.close();
    }
    private Object leeObjeto(SocketChannel client) throws IOException, ClassNotFoundException {
        ByteBuffer b = ByteBuffer.allocate(20000);
        b.clear();
        client.read(b);
        b.flip();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b.array()));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public void esperaParaLeer() throws IOException {
        while (true){
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                if (key.isWritable()){
                    key.interestOps(SelectionKey.OP_READ);
                }
                if(key.isReadable()) {
                    return;
                }
            }
        }
    }

    public void mostrarArchivos(SocketChannel client) throws IOException, ClassNotFoundException {
        esperaParaLeer();
        File[] listaDeArchivos = (File[]) leeObjeto(client);
        if (listaDeArchivos == null || listaDeArchivos.length == 0)
            System.out.println("Directorio vacio");
        else {
            System.out.print("\n**** Archivos en " + dirActual + " ****" + "\n\n");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            for (File archivo : listaDeArchivos) {
                if (archivo.isDirectory())
                    System.out.printf("- (dir) %s  -- %s%n",
                            archivo.getName(),
                            sdf.format(archivo.lastModified())
                    );
            }
            for (File archivo : listaDeArchivos) {
                if (!archivo.isDirectory())
                    System.out.printf("- (file) %s -- %d kb -- %s%n",
                            archivo.getName(),
                            archivo.length() / 1024,
                            sdf.format(archivo.lastModified())
                    );
            }
        }
    }


    public void subir(SocketChannel client){
        try {
            JFileChooser jf = new JFileChooser();
            jf.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);  //para integrar en una sola opcion del menú el subir archivos y carpetas
            int r = jf.showOpenDialog(null);
            if(r==JFileChooser.APPROVE_OPTION){
                File f = jf.getSelectedFile();

                if(f.isDirectory()){
                    String nomzip = f.getAbsolutePath()+".zip";
                    ZipUtil.pack(f, new File(nomzip));
                    subirArchivo(new File(nomzip) , client);
                    new File(nomzip).delete();
                }
                else subirArchivo(f, client);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void subirArchivo(File f, SocketChannel client) throws IOException, ClassNotFoundException {
        long tam = f.length();
        System.out.println("Enviando '"+f.getName()+"' de "+tam/1024+" kb");
        escribeObjeto(f,client);

        DataInputStream disf = new DataInputStream(new FileInputStream(f.getAbsolutePath()));
        long enviados = 0;
        int l;
        while (enviados<tam){
            byte[] b = new byte[1500];
            l=disf.read(b);
            ByteBuffer buffer = ByteBuffer.wrap(b);
            client.write(buffer);
            //bloquearse hasta que el server haya leido
            esperaParaLeer();
            leeObjeto(client);
            enviados += l;
        }
        disf.close();
        System.out.println("Archivo enviado");
    }

    public void descargar(SocketChannel client) throws IOException, ClassNotFoundException {
        Scanner reader = new Scanner(System.in);
        System.out.println("Archivo o dir a descargar: ");
        String elecdow = reader.nextLine();
        escribeObjeto(elecdow,client);
        esperaParaLeer();
        String respuesta = (String) leeObjeto(client);
        escribeObjeto("",client);
        System.out.println(respuesta);

        if(respuesta.equals("El archivo o dir no existe"))
            return;

        esperaParaLeer();
        File f = (File)leeObjeto(client);
        String fileName = f.toString();
        String aux = FilenameUtils.getExtension(fileName);

        if(aux.equals("zip")) {
            System.out.println("\nDeseas descargar una carpeta");
            bajarDir(f,client);
        }
        else {
            System.out.println("\nDeseas descargar un archivo");
            bajarArchivo(f,client);
        }
    }

    public void bajarDir(File f,SocketChannel client) throws IOException{

        System.out.println("carpeta a descargar:"+f.getName());

        bajarArchivo(f,client);

        String destino = Paths.get(ruta, f.getName()).toString();
        Path descom = Paths.get(ruta, FilenameUtils.removeExtension(f.getName()) );
        System.out.println("Descomprimiendo " + destino + " en " + descom);

        new net.lingala.zip4j.ZipFile(destino).extractAll(descom.toString());
        new File(destino).delete();
    }

    public void bajarArchivo(File f,SocketChannel client) {
        long tam = f.length();

        System.out.println("Comienza descarga del archivo '"+f.getName()+"' de "+tam/1024+" kb");

        try {
            System.out.println("File chooser lanzado");
            JFileChooser jf = new JFileChooser();
            jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int r = jf.showOpenDialog(null);
            if(r==JFileChooser.APPROVE_OPTION) {
                File aux = jf.getSelectedFile();
                ruta = aux.getAbsolutePath();

                System.out.println("Descargando en: " +ruta);

                DataOutputStream dosf = new DataOutputStream(new FileOutputStream(Paths.get(ruta,f.getName()).toString()));

                long recibidos = 0;
                int l;
                while (recibidos < tam) {
                    ByteBuffer b = ByteBuffer.allocate(1500);
                    b.clear();
                    esperaParaLeer();
                    client.read(b);
                    b.flip();
                    l = b.limit();
                    dosf.write(b.array(),0,l);
                    dosf.flush();
                    recibidos += l;
                    escribeObjeto("",client);
                }
                System.out.println("Archivo recibido");
                dosf.close();
            }
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void eliminar(SocketChannel client) throws IOException, ClassNotFoundException {     //trabajo en este
        Scanner reader = new Scanner(System.in);
        System.out.println("Archivo o dir a eliminar: ");
        String elec = reader.nextLine();
        escribeObjeto(elec, client);
        esperaParaLeer();
        System.out.println( (String) leeObjeto(client) );
    }

    public void cambiarDir(SocketChannel client) throws IOException, ClassNotFoundException{
        Scanner reader = new Scanner(System.in);
        System.out.println("Nombre del dir: ");
        if(!dirActual.equals("drive"))  //mostrar la opción de "atrás" para volver a la raíz (drive\)
            System.out.println("( .. para volver al inicio )");

        String elec = reader.nextLine();
        escribeObjeto(elec,client);

        esperaParaLeer();
        String respuesta = (String) leeObjeto(client);

        if(respuesta.equals("no")){
            elec = "";
            System.out.println("Dir invalido");
        }
        if(elec.equals(".."))
            dirActual = "drive";
        else if(!elec.equals(""))
            dirActual = dirActual + "\\" + elec;
        mostrarArchivos(client);
    }
    public static void main(String[] args) {
        new NIOclient();
    }
}
