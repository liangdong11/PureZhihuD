package io.github.laucherish.purezhihud.ui.fragment;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.yalantis.phoenix.PullToRefreshView;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import io.github.laucherish.purezhihud.R;
import io.github.laucherish.purezhihud.base.BaseFragment;
import io.github.laucherish.purezhihud.bean.News;
import io.github.laucherish.purezhihud.bean.NewsDetail;
import io.github.laucherish.purezhihud.bean.NewsList;
import io.github.laucherish.purezhihud.db.dao.NewDao;
import io.github.laucherish.purezhihud.network.manager.RetrofitManager;
import io.github.laucherish.purezhihud.ui.adapter.AutoLoadOnScrollListener;
import io.github.laucherish.purezhihud.ui.adapter.NewsListAdapter;
import io.github.laucherish.purezhihud.utils.L;
import io.github.laucherish.purezhihud.utils.NetUtil;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by laucherish on 16/3/16.
 */
public class NewsListFragment extends BaseFragment implements PullToRefreshView.OnRefreshListener {

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    @Bind(R.id.tv_load_empty)
    TextView mTvLoadEmpty;
    @Bind(R.id.tv_load_error)
    TextView mTvLoadError;
    @Bind(R.id.pb_loading)
    ContentLoadingProgressBar mPbLoading;
    @Bind(R.id.rcv_news_list)
    RecyclerView mRcvNewsList;
    @Bind(R.id.ptr_news_list)
    PullToRefreshView mPtrNewsList;

    private NewsListAdapter mNewsListAdapter;
    private String curDate;
    private AutoLoadOnScrollListener mAutoLoadListener;
    private Snackbar mLoadLatestSnackbar;
    private Snackbar mLoadBeforeSnackbar;

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_news_list;
    }

    @Override
    protected void afterCreate(Bundle savedInstanceState) {
        init();
        loadLatestNews();
    }

    public static NewsListFragment newInstance() {
        return new NewsListFragment();
    }

    private void init() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setSupportActionBar(mToolbar);

        mPtrNewsList.setOnRefreshListener(this);

        //配置RecyclerView
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mRcvNewsList.setLayoutManager(linearLayoutManager);
        mRcvNewsList.setHasFixedSize(true);
        mRcvNewsList.setItemAnimator(new DefaultItemAnimator());
        mNewsListAdapter = new NewsListAdapter(getActivity(), new ArrayList<News>());
        mRcvNewsList.setAdapter(mNewsListAdapter);
        mAutoLoadListener = new AutoLoadOnScrollListener(linearLayoutManager) {
            @Override
            public void onLoadMore(int currentPage) {
                loadBeforeNews(curDate);
            }
        };
        mRcvNewsList.addOnScrollListener(mAutoLoadListener);

        mLoadLatestSnackbar = Snackbar.make(mRcvNewsList, R.string.load_fail, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.refresh, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadLatestNews();
                    }
                });
        mLoadBeforeSnackbar = Snackbar.make(mRcvNewsList, R.string.load_more_fail, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.refresh, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadBeforeNews(curDate);
                    }
                });
    }

    private void loadLatestNews() {
        RetrofitManager.builder().getLatestNews()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        showProgress();
                    }
                })
                .map(new Func1<NewsList, NewsList>() {
                    @Override
                    public NewsList call(NewsList newsList) {
                        cacheAllDetail(newsList.getStories());
                        return changeReadState(newsList);
                    }
                })
                .subscribe(new Action1<NewsList>() {
                    @Override
                    public void call(NewsList newsList) {
                        mPtrNewsList.setRefreshing(false);
                        hideProgress();
                        L.object(newsList.getStories());
                        if (newsList.getStories() == null) {
                            mTvLoadEmpty.setVisibility(View.VISIBLE);
                        } else {
                            mNewsListAdapter.changeData(newsList.getStories());
                            curDate = newsList.getDate();
                            mTvLoadEmpty.setVisibility(View.GONE);
                        }
                        mTvLoadError.setVisibility(View.GONE);
                        mLoadLatestSnackbar.dismiss();
                        if (newsList.getStories().size() < 8) {
                            loadBeforeNews(curDate);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mPtrNewsList.setRefreshing(false);
                        hideProgress();
                        mLoadLatestSnackbar.show();
                        mTvLoadError.setVisibility(View.VISIBLE);
                        mTvLoadEmpty.setVisibility(View.GONE);
                    }
                });

    }

    private void loadBeforeNews(String date) {
        RetrofitManager.builder().getBeforeNews(date)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Func1<NewsList, NewsList>() {
                    @Override
                    public NewsList call(NewsList newsList) {
                        cacheAllDetail(newsList.getStories());
                        return changeReadState(newsList);
                    }
                })
                .subscribe(new Action1<NewsList>() {
                    @Override
                    public void call(NewsList newsList) {
                        mAutoLoadListener.setLoading(false);
                        mLoadBeforeSnackbar.dismiss();
                        L.object(newsList.getStories());
                        mNewsListAdapter.addData(newsList.getStories());
                        curDate = newsList.getDate();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mAutoLoadListener.setLoading(false);
                        L.e(throwable, "Load before news error");
                        mLoadBeforeSnackbar.show();
                    }
                });
    }

    public NewsList changeReadState(NewsList newsList) {
        List<String> allReadId = new NewDao(getActivity()).getAllReadNew();
        for (News news : newsList.getStories()) {
            news.setDate(newsList.getDate());
            for (String readId : allReadId) {
                if (readId.equals(news.getId() + "")) {
                    news.setRead(true);
                }
            }
        }
        return newsList;
    }

    private void cacheAllDetail(List<News> newsList) {
        if (NetUtil.isWifiConnected()) {
            for (News news : newsList) {
                L.d("Cache news: " + news.getId() + news.getTitle());
                cacheNewsDetail(news.getId());
            }
        }
    }

    private void cacheNewsDetail(int newsId) {
        RetrofitManager.builder().getNewsDetail(newsId)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(new Action1<NewsDetail>() {
                    @Override
                    public void call(NewsDetail newsDetail) {
//                        ArrayList<String> imgList = getImgs(newsDetail.getBody());
//                        for (String img : imgList) {
//                            L.d("Cache img: " + img);
//                        }
                    }
                });
    }

    /**
     * 获取NewsDetail里面的图片url
     */
//    private ArrayList<String> getImgs(String html) {
//
//        ArrayList<String> imgList = new ArrayList<>();
//
//        Document doc = Jsoup.parse(html);
//        Elements es = doc.getElementsByTag("img");
//
//        for (Element e : es) {
//            String src = e.attr("src");
//
//            String newImgUrl = src.replaceAll("\"", "");
//            newImgUrl = newImgUrl.replace('\\', ' ');
//            newImgUrl = newImgUrl.replaceAll(" ", "");
//
//            if (!TextUtils.isEmpty(newImgUrl)) {
//                imgList.add(newImgUrl);
//            }
//        }
//
//        return imgList;
//    }

    @Override
    public void onRefresh() {
        loadLatestNews();
    }

    public void showProgress() {
        mPbLoading.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        mPbLoading.setVisibility(View.GONE);
    }

}
