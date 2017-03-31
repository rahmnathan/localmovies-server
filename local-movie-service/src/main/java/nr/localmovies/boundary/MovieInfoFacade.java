package nr.localmovies.boundary;

import nr.localmovies.control.FileListProvider;
import nr.localmovies.control.MovieInfoControl;
import nr.localmovies.data.MovieSearchCriteria;
import nr.localmovies.directory.monitor.DirectoryMonitor;
import nr.localmovies.movieinfoapi.MovieInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class MovieInfoFacade {
    @Value("${media.path}")
    private String mediaPath;
    private MovieInfoControl movieInfoControl;
    private DirectoryMonitor directoryMonitor;
    private FileListProvider fileListProvider;

    @Autowired
    public MovieInfoFacade(MovieInfoControl movieInfoControl, DirectoryMonitor directoryMonitor, FileListProvider fileListProvider){
        this.movieInfoControl = movieInfoControl;
        this.directoryMonitor = directoryMonitor;
        this.fileListProvider = fileListProvider;
    }

    @PostConstruct
    public void startDirectoryMonitor(){
        if(!mediaPath.equalsIgnoreCase("none")) {
            directoryMonitor.addObserver(fileListProvider);
            directoryMonitor.startRecursiveWatcher(mediaPath);
        }
    }

    public int loadMovieListLength(String directoryPath){
        return fileListProvider.listFiles(directoryPath).length;
    }

    public List<MovieInfo> loadMovieList(MovieSearchCriteria searchCriteria) {
        List<File> files = Arrays.asList(fileListProvider.listFiles(searchCriteria.getPath()));

        return files.parallelStream()
                .sorted()
                .skip(searchCriteria.getPage() * searchCriteria.getItemsPerPage())
                .limit(searchCriteria.getItemsPerPage())
                .map(file -> movieInfoControl.loadMovieInfoFromCache(file.getAbsolutePath()))
                .collect(Collectors.toList());
    }

    public MovieInfo loadSingleMovie(String filePath) throws ExecutionException {
        return movieInfoControl.loadMovieInfoFromCache(filePath);
    }
}